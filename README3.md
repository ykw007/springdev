아래 예시는 **Oracle 멀티 DB 구성**에서

* **SELECT(조회)는 운영 DB**
* **UPDATE/INSERT(쓰기)는 개발 DB**
  로 분리하여 동작하도록 전체 코드를 구성한 것입니다.

환경 가정: **Spring Boot 2.3.x, Spring Batch 4.2.x, Java 17, MyBatis-Spring, Oracle 12c+**
핵심 포인트:

* DataSource 2개(`prod`, `dev`) + SqlSessionFactory 2개 + TxManager 2개
* **Reader(MyBatisPagingItemReader)**는 **운영(prod) SqlSessionFactory** 사용
* **Writer(Service)**는 **운영(prod)에서 의미조건 조회 → 개발(dev)에 MERGE(UPSERT)**
* 배치 Step의 트랜잭션 매니저는 **devTxManager**(쓰기 DB 기준) 사용
* Mapper 인터페이스를 **읽기용 패키지**와 **쓰기용 패키지**로 분리하여 각기 다른 팩토리에 바인딩

---

# 0) Gradle 의존성 (예시)

```gradle
plugins {
    id 'org.springframework.boot' version '2.3.12.RELEASE'
    id 'io.spring.dependency-management' version '1.0.15.RELEASE'
    id 'java'
}

group = 'com.example'
version = '1.0.0'
sourceCompatibility = '17'

repositories { mavenCentral() }

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-batch'
    implementation 'org.springframework.boot:spring-boot-starter-jdbc'
    implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:2.1.4'

    runtimeOnly 'com.oracle.database.jdbc:ojdbc8:19.19.0.0'

    compileOnly 'org.projectlombok:lombok:1.18.34'
    annotationProcessor 'org.projectlombok:lombok:1.18.34'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.batch:spring-batch-test'
}
```

---

# 1) `application.yml` (운영/개발 DB 분리)

```yaml
spring:
  batch:
    initialize-schema: never

  datasource:
    prod:
      url: jdbc:oracle:thin:@//PROD_HOST:1521/PROD_SERVICE
      username: PROD_USER
      password: PROD_PASS
      driver-class-name: oracle.jdbc.OracleDriver

    dev:
      url: jdbc:oracle:thin:@//DEV_HOST:1521/DEV_SERVICE
      username: DEV_USER
      password: DEV_PASS
      driver-class-name: oracle.jdbc.OracleDriver

mybatis:
  configuration:
    map-underscore-to-camel-case: true
    jdbc-type-for-null: 'NULL'
  # 각 팩토리에서 mapper-locations를 개별 지정할 예정이므로 전역 지정은 생략 가능
  # (해도 무방, 여기서는 팩토리별 config에서 지정)

logging:
  level:
    root: INFO
    com.example.batch: DEBUG
```

---

# 2) 도메인

## `MyItem.java`

```java
package com.example.batch.domain;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MyItem {
    private Long id;               // optional
    private String bizKey;         // UNIQUE key
    private String payload;
    private LocalDateTime eventTs; // read window 기준 시간
}
```

## `MyRow.java`

```java
package com.example.batch.domain;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MyRow {
    private Long id;               // PK
    private String bizKey;         // UNIQUE
    private String payload;
    private LocalDateTime updatedAt;
}
```

## `Condition.java`

```java
package com.example.batch.domain;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Condition {
    private String bizKey;

    public static Condition from(MyItem item) {
        return Condition.builder().bizKey(item.getBizKey()).build();
    }
}
```

## `MyRowMapperUtil.java`

```java
package com.example.batch.domain;

import java.time.LocalDateTime;
import java.util.List;

public final class MyRowMapperUtil {
    private MyRowMapperUtil(){}

    public static MyRow from(MyItem item, List<MyRow> existing) {
        Long id = existing.isEmpty() ? null : existing.get(0).getId();
        return MyRow.builder()
                .id(id)
                .bizKey(item.getBizKey())
                .payload(item.getPayload())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
```

---

# 3) 멀티 DataSource / MyBatis 설정

## `DataSourceConfig.java`

```java
package com.example.batch.config;

import lombok.Data;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.*;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    /* -------------------- PROD (읽기) -------------------- */
    @Bean(name = "prodDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.prod")
    public DataSource prodDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "prodSqlSessionFactory")
    public SqlSessionFactory prodSqlSessionFactory(
            @Qualifier("prodDataSource") DataSource ds) throws Exception {
        SqlSessionFactoryBean fb = new SqlSessionFactoryBean();
        fb.setDataSource(ds);
        fb.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/prod/*.xml"));
        // map-underscore-to-camel-case 등 전역 설정은 application.yml의 mybatis.configuration 반영
        return fb.getObject();
    }

    @Bean(name = "prodSqlSessionTemplate")
    public SqlSessionTemplate prodSqlSessionTemplate(
            @Qualifier("prodSqlSessionFactory") SqlSessionFactory f) {
        return new SqlSessionTemplate(f); // SIMPLE
    }

    @Bean(name = "prodTxManager")
    public DataSourceTransactionManager prodTxManager(
            @Qualifier("prodDataSource") DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }

    @Configuration
    @MapperScan(
            basePackages = "com.example.batch.mapper.read",
            sqlSessionFactoryRef = "prodSqlSessionFactory"
    )
    static class ProdMapperScan {}

    /* -------------------- DEV (쓰기) -------------------- */
    @Bean(name = "devDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.dev")
    public DataSource devDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "devSqlSessionFactory")
    public SqlSessionFactory devSqlSessionFactory(
            @Qualifier("devDataSource") DataSource ds) throws Exception {
        SqlSessionFactoryBean fb = new SqlSessionFactoryBean();
        fb.setDataSource(ds);
        fb.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/dev/*.xml"));
        return fb.getObject();
    }

    @Bean(name = "devSqlSessionTemplate")
    public SqlSessionTemplate devSqlSessionTemplate(
            @Qualifier("devSqlSessionFactory") SqlSessionFactory f) {
        return new SqlSessionTemplate(f); // SIMPLE
    }

    @Bean(name = "devTxManager")
    public DataSourceTransactionManager devTxManager(
            @Qualifier("devDataSource") DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }

    @Configuration
    @MapperScan(
            basePackages = "com.example.batch.mapper.write",
            sqlSessionFactoryRef = "devSqlSessionFactory"
    )
    static class DevMapperScan {}
}
```

> **핵심**: `mapper/read` 패키지 인터페이스는 **prod** 팩토리에 바인딩, `mapper/write` 패키지는 **dev** 팩토리에 바인딩.

---

# 4) 매퍼 인터페이스 & XML

## 읽기용 매퍼 인터페이스 (운영 DB)

### `mapper/read/ReadMyItemMapper.java`

```java
package com.example.batch.mapper.read;

import com.example.batch.domain.MyItem;
import com.example.batch.domain.MyRow;
import com.example.batch.domain.Condition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReadMyItemMapper {

    // 의미조건 조회(운영에서 확인)
    List<MyRow> selectMeaningfulRows(Condition cond);

    // 10분 구간 전체 조회 (RowBounds 페이징)
    List<MyItem> selectForWindow(@Param("from") LocalDateTime from,
                                 @Param("to")   LocalDateTime to);

    int countForWindow(@Param("from") LocalDateTime from,
                       @Param("to")   LocalDateTime to);
}
```

### `resources/mapper/prod/ReadMyItemMapper.xml`

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="com.example.batch.mapper.read.ReadMyItemMapper">

    <resultMap id="MyRowMap" type="com.example.batch.domain.MyRow">
        <id     property="id"        column="ID"/>
        <result property="bizKey"    column="BIZ_KEY"/>
        <result property="payload"   column="PAYLOAD"/>
        <result property="updatedAt" column="UPDATED_AT"/>
    </resultMap>

    <!-- 의미조건 조회: 운영 DB에서 현재 상태 확인 -->
    <select id="selectMeaningfulRows"
            parameterType="com.example.batch.domain.Condition"
            resultMap="MyRowMap">
        SELECT ID, BIZ_KEY, PAYLOAD, UPDATED_AT
          FROM MY_TABLE
         WHERE BIZ_KEY = #{bizKey}
         ORDER BY ID ASC
    </select>

    <!-- 10분 구간 조회 (정렬만, RowBounds=pageSize=10) -->
    <select id="selectForWindow" resultType="com.example.batch.domain.MyItem">
        SELECT ID,
               BIZ_KEY   AS bizKey,
               PAYLOAD   AS payload,
               EVENT_TS  AS eventTs
          FROM MY_TABLE
         WHERE EVENT_TS >= #{from}
           AND EVENT_TS  < #{to}
         ORDER BY ID ASC
    </select>

    <select id="countForWindow" resultType="int">
        SELECT COUNT(*)
          FROM MY_TABLE
         WHERE EVENT_TS >= #{from}
           AND EVENT_TS  < #{to}
    </select>

</mapper>
```

## 쓰기용 매퍼 인터페이스 (개발 DB)

### `mapper/write/WriteMyItemMapper.java`

```java
package com.example.batch.mapper.write;

import com.example.batch.domain.MyRow;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WriteMyItemMapper {
    int mergeUpsert(MyRow row); // 개발 DB에 UPSERT
}
```

### `resources/mapper/dev/WriteMyItemMapper.xml`

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="com.example.batch.mapper.write.WriteMyItemMapper">

    <!-- Oracle MERGE (개발 DB로 쓰기) -->
    <update id="mergeUpsert" parameterType="com.example.batch.domain.MyRow">
        MERGE INTO MY_TABLE t
        USING (
            SELECT
                #{bizKey}     AS BIZ_KEY,
                #{payload}    AS PAYLOAD,
                #{updatedAt}  AS UPDATED_AT
            FROM DUAL
        ) s
        ON (t.BIZ_KEY = s.BIZ_KEY)
        WHEN MATCHED THEN
            UPDATE SET
                t.PAYLOAD    = s.PAYLOAD,
                t.UPDATED_AT = s.UPDATED_AT
        WHEN NOT MATCHED THEN
            INSERT (ID, BIZ_KEY, PAYLOAD, UPDATED_AT)
            VALUES (DEFAULT, s.BIZ_KEY, s.PAYLOAD, s.UPDATED_AT)
    </update>

</mapper>
```

> 개발 DB의 `MY_TABLE`에도 운영과 동일 스키마/제약(특히 `BIZ_KEY` UNIQUE)이 있어야 합니다.

---

# 5) 리포지토리 (읽기용/쓰기용 분리)

## `repository/ReadRepository.java`

```java
package com.example.batch.repository;

import com.example.batch.domain.Condition;
import com.example.batch.domain.MyItem;
import com.example.batch.domain.MyRow;
import com.example.batch.mapper.read.ReadMyItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ReadRepository {
    private final ReadMyItemMapper mapper;

    public List<MyRow> selectMeaningfulRows(Condition cond) {
        return mapper.selectMeaningfulRows(cond);
    }

    public List<MyItem> selectForWindow(LocalDateTime from, LocalDateTime to) {
        return mapper.selectForWindow(from, to);
    }

    public int countForWindow(LocalDateTime from, LocalDateTime to) {
        return mapper.countForWindow(from, to);
    }
}
```

## `repository/WriteRepository.java`

```java
package com.example.batch.repository;

import com.example.batch.domain.MyRow;
import com.example.batch.mapper.write.WriteMyItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WriteRepository {
    private final WriteMyItemMapper mapper;

    public int mergeUpsert(MyRow row) {
        return mapper.mergeUpsert(row);
    }
}
```

---

# 6) 서비스 (운영에서 조회 → 개발에 MERGE)

## `service/MyItemService.java`

```java
package com.example.batch.service;

import com.example.batch.domain.*;
import com.example.batch.repository.ReadRepository;
import com.example.batch.repository.WriteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 트랜잭션 매니저는 devTxManager를 사용(쓰기 기준).
 * 운영 DB 읽기는 별도 Tx 경계에 묶이지 않아도 무방(단순 조회).
 * 필요하면 읽기 전용 보장용으로 별도 readOnly Tx 어노테이션을 추가할 수 있음.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MyItemService {

    private final ReadRepository readRepo;   // 운영 DB
    private final WriteRepository writeRepo; // 개발 DB

    @Transactional(transactionManager = "devTxManager") // 쓰기 DB 기준 트랜잭션
    public void add(MyItem item) {
        // 1) 운영 DB에서 의미조건 조회
        Condition cond = Condition.from(item);
        List<MyRow> existing = readRepo.selectMeaningfulRows(cond);

        // 2) 변환
        MyRow toSave = MyRowMapperUtil.from(item, existing);

        // 3) 개발 DB에 MERGE(UPSERT)
        int cnt = writeRepo.mergeUpsert(toSave);
        log.debug("[service.add] bizKey={}, mergeUpsertCount={}", item.getBizKey(), cnt);
    }
}
```

> 운영 DB 읽기를 트랜잭션에 묶고 싶다면 `@Transactional(readOnly = true, transactionManager = "prodTxManager")` 로 **별도 메서드**를 분리해 호출하는 방법도 있습니다. (여기서는 단순 조회로 처리)

---

# 7) 배치 설정 (Reader=운영, Writer=개발)

## `config/PagingJobConfig.java`

```java
package com.example.batch.config;

import com.example.batch.domain.MyItem;
import com.example.batch.service.MyItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.batch.MyBatisPagingItemReader;
import org.mybatis.spring.batch.builder.MyBatisPagingItemReaderBuilder;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.*;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.SynchronizedItemStreamReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@EnableBatchProcessing
@RequiredArgsConstructor
public class PagingJobConfig {

    private final JobBuilderFactory jobBuilderFactory;
    private final StepBuilderFactory stepBuilderFactory;

    // 운영 DB 팩토리(Reader용)
    @Qualifier("prodSqlSessionFactory")
    private final SqlSessionFactory prodSqlSessionFactory;

    // 개발 DB TxManager(Writer 기준 트랜잭션)
    @Qualifier("devTxManager")
    private final DataSourceTransactionManager devTxManager;

    private final MyItemService myItemService;

    @Bean
    public Job pagingJob() {
        return jobBuilderFactory.get("pagingJob")
                .incrementer(new RunIdIncrementer())
                .start(pagingStep())
                .build();
    }

    @Bean
    public Step pagingStep() {
        return stepBuilderFactory.get("pagingStep")
                .<MyItem, MyItem>chunk(10)
                .reader(reader())
                .writer(writer())
                .taskExecutor(taskExecutor())
                .throttleLimit(8)
                .transactionManager(devTxManager) // 쓰기 DB 기준 트랜잭션
                .build();
    }

    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(8);
        ex.setMaxPoolSize(8);
        ex.setQueueCapacity(0);
        ex.setThreadNamePrefix("paging-");
        ex.initialize();
        return ex;
    }

    // 멀티스레드 안전 래퍼
    @Bean
    public ItemStreamReader<MyItem> reader() {
        SynchronizedItemStreamReader<MyItem> sync = new SynchronizedItemStreamReader<>();
        sync.setDelegate(delegateReader(null, null));
        return sync;
    }

    // 운영 DB로부터 10분 구간 페이징(정렬만, RowBounds=pageSize)
    @Bean
    @StepScope
    public MyBatisPagingItemReader<MyItem> delegateReader(
            @Value("#{jobParameters['from']}") String fromIso,
            @Value("#{jobParameters['to']}")   String toIso
    ) {
        Map<String, Object> params = new HashMap<>();
        params.put("from", fromIso);
        params.put("to",   toIso);

        return new MyBatisPagingItemReaderBuilder<MyItem>()
                .sqlSessionFactory(prodSqlSessionFactory) // 운영 DB
                .queryId("com.example.batch.mapper.read.ReadMyItemMapper.selectForWindow")
                .parameterValues(params)
                .pageSize(10)
                .build();
    }

    @Bean
    public ItemWriter<MyItem> writer() {
        return items -> {
            final String thread = Thread.currentThread().getName();
            log.info("[writer] thread={}, items={}", thread, items.size());
            for (MyItem item : items) {
                log.debug("[writer] thread={} bizKey={}", thread, item.getBizKey());
                myItemService.add(item); // 내부에서 운영 읽기 → 개발 MERGE
            }
        };
    }
}
```

---

# 8) Oracle 스키마 (운영/개발 동일 구조)

```sql
-- 운영/개발 모두 동일 스키마 필요 (특히 BIZ_KEY UNIQUE)
CREATE TABLE MY_TABLE (
  ID         NUMBER GENERATED BY DEFAULT AS IDENTITY,
  BIZ_KEY    VARCHAR2(200) NOT NULL,
  PAYLOAD    CLOB NULL,
  EVENT_TS   TIMESTAMP NOT NULL,   -- 조회 윈도우 컬럼
  UPDATED_AT TIMESTAMP NOT NULL,
  CONSTRAINT PK_MY_TABLE PRIMARY KEY (ID),
  CONSTRAINT UK_MY_TABLE_BIZ_KEY UNIQUE (BIZ_KEY)
);
```

---

## 운영 팁

* 배치 Step 트랜잭션은 **devTxManager** 기준이므로, **개발 DB 쓰기**는 커밋/롤백 일관성 보장.
  운영 DB 읽기는 트랜잭션 밖에서 수행되어도 일반적으로 문제없지만, **읽기 일관성**이 필요하면
  별도 `@Transactional(readOnly=true, transactionManager="prodTxManager")` 메서드로 감싸 호출하도록 구조화하세요.
* MyBatis ExecutorType은 **SIMPLE로 통일**(본 예시의 템플릿 기본).
  동일 Step 트랜잭션 중에 타입 변경을 시도하지 않으므로 **ExecutorType 충돌 없음**.
* 성능이 필요하면 **파티셔닝**(시간창/키범위 기반) 병렬화도 고려하세요.

---

필요하시면, **운영 읽기에도 별도 트랜잭션(일관성 보장)** 을 적용한 버전이나, **수동 OFFSET/FETCH(12c+) 쿼리 기반의 커스텀 리더/파티셔너** 버전으로도 확장해 드릴게요.
