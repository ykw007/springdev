package com.example.demo;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
 
@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

}
/*
Spring Boot 애플리케이션을 경량화하여 빠르게 기동하는 방법은 여러 가지가 있습니다. 주요 최적화 방법을 정리하면 다음과 같습니다.

### 1. **의존성 최소화**
   - 필요한 라이브러리만 포함하여 불필요한 의존성을 제거합니다. Spring Boot Starter로 제공되는 의존성 중 실제로 사용하지 않는 것이 있는지 확인하고, 줄이면 애플리케이션 크기와 기동 시간이 줄어듭니다.

### 2. **Spring Boot 시작 클래스 최적화**
   - `@SpringBootApplication` 클래스에 사용하지 않는 자동 구성(ex: JPA, Web 등)을 제외하여 불필요한 설정을 피할 수 있습니다.
   ```java
   @SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
   ```

### 3. **레이어드 아키텍처 조정**
   - 기동 시 로딩해야 할 서비스, 리포지토리 등의 빈을 필요한 부분으로 제한합니다. 예를 들어, 꼭 필요하지 않은 모듈이나 서비스는 따로 분리하거나 프로파일을 지정하여 특정 프로파일로만 로드되도록 합니다.

### 4. **빈 로딩 최적화**
   - `@Lazy` 어노테이션을 사용해 초기 기동 시점에 꼭 필요하지 않은 빈들은 필요한 시점에 로드되도록 지연 로딩합니다.

### 5. **Spring Native 사용**
   - Spring Native는 Spring Boot 애플리케이션을 GraalVM을 사용해 네이티브 이미지로 변환해 주므로 경량화 및 기동 시간 최적화에 효과적입니다. 다만 네이티브 이미지로 컴파일 시 추가 설정이 필요합니다.

### 6. **JVM 튜닝**
   - `-Xms`, `-Xmx`, `-XX:+UseSerialGC` 등의 JVM 옵션을 통해 메모리 사용량을 조정하고, 가비지 컬렉션을 최적화하여 빠른 기동을 도모할 수 있습니다.

### 7. **캐싱 및 준비된 리소스 활용**
   - 빈 생성을 줄이기 위해 자주 사용되는 데이터는 캐시 메커니즘을 사용하고, 애플리케이션 기동 시 준비된 리소스를 활용해 처리량을 높입니다.

### 8. **DevTools 비활성화**
   - 개발 환경에서 사용하는 DevTools는 프로덕션에서는 필요하지 않으므로 `spring-boot-devtools` 의존성을 제외하거나 비활성화하여 기동 시간을 줄입니다.

이러한 방법들을 통해 Spring Boot 애플리케이션의 기동 시간을 줄이고 더 경량화된 구동 환경을 만들 수 있습니다.



Spring Boot 애플리케이션에서 HikariCP를 이용하여 초경량으로 DB 연결만 수행하려면, 불필요한 기능을 제거하고 필수적인 DB 연결 설정만 남기는 방식으로 최적화할 수 있습니다. 이를 위해 HikariCP와 관련된 최소한의 설정과 빠른 기동을 위한 옵션을 추가할 수 있습니다.

### 설정 방법

#### 1. **HikariCP를 이용한 최소 DB 연결 설정**
   - `application.properties`나 `application.yml`에 HikariCP 설정을 최소한으로 추가합니다. HikariCP는 기본적으로 Spring Boot 2.x 이상에서 기본 설정되므로, 필요 설정만 추가하면 됩니다.
   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/yourdb
       username: yourusername
       password: yourpassword
       driver-class-name: com.mysql.cj.jdbc.Driver
       hikari:
         maximum-pool-size: 5              # 커넥션 풀 크기 최소화
         minimum-idle: 1                   # 최소 유휴 커넥션 수 최소화
         idle-timeout: 10000               # 유휴 커넥션 유지 시간(ms)
         connection-timeout: 2000          # 커넥션 획득 대기 시간(ms)
         pool-name: "HikariPool"           # 풀 이름 설정
   ```

#### 2. **불필요한 자동 설정 제외하기**
   - DB 연결만 필요하므로, Web, Security, JPA 등 불필요한 기능을 비활성화하여 경량화합니다.
   ```java
   @SpringBootApplication(exclude = {
       WebMvcAutoConfiguration.class,
       SecurityAutoConfiguration.class,
       HibernateJpaAutoConfiguration.class
   })
   ```

#### 3. **Lazy Initialization 활성화**
   - `spring.main.lazy-initialization=true`를 추가하여 필요할 때만 빈을 로딩하도록 설정하여, 초기 기동 시 불필요한 리소스를 최소화합니다.
   ```properties
   spring.main.lazy-initialization=true
   ```

#### 4. **CommandLineRunner로 DB 연결만 테스트 후 종료**
   - 단순 DB 연결만 확인하고 기동 후 종료하려면 `CommandLineRunner`를 사용하여 DB 연결 테스트 후 애플리케이션을 종료할 수 있습니다.
   ```java
   @SpringBootApplication
   public class Application implements CommandLineRunner {
       @Autowired
       private DataSource dataSource;

       public static void main(String[] args) {
           SpringApplication.run(Application.class, args);
       }

       @Override
       public void run(String... args) throws Exception {
           try (Connection conn = dataSource.getConnection()) {
               System.out.println("DB Connection Successful: " + conn.isValid(2));
           } catch (SQLException e) {
               e.printStackTrace();
           }
           System.exit(0);  // 애플리케이션 종료
       }
   }
   ```

#### 5. **JVM 최적화 옵션**
   - 서버 환경에 맞게 JVM 메모리 및 가비지 컬렉터 설정을 조정하여 최적화할 수 있습니다.
   - 예: `-Xms128m -Xmx128m -XX:+UseSerialGC` (경량화된 애플리케이션에 적합한 GC 설정)

이와 같이 HikariCP와 관련된 최소 설정을 통해 빠르고 경량화된 Spring Boot 애플리케이션을 구성할 수 있습니다.



`CommandLineRunner`를 사용하지 않고, DB 연결만을 확인하며 초경량으로 Spring Boot 애플리케이션을 기동하려면 다음과 같은 방법을 사용할 수 있습니다. 주로 애플리케이션이 서버로 동작할 때 유용한 방법입니다.

### 1. **RestController로 간단한 Health Check API 구성**
   - DB 연결 확인을 위한 간단한 REST API를 구성하여 HTTP 요청을 통해 DB 상태를 확인할 수 있습니다. 이 방법은 `CommandLineRunner` 없이도 연결 상태를 검증할 수 있는 장점이 있습니다.
   ```java
   import org.springframework.beans.factory.annotation.Autowired;
   import org.springframework.web.bind.annotation.GetMapping;
   import org.springframework.web.bind.annotation.RestController;

   import javax.sql.DataSource;
   import java.sql.Connection;
   import java.sql.SQLException;

   @RestController
   public class HealthCheckController {
       @Autowired
       private DataSource dataSource;

       @GetMapping("/health")
       public String healthCheck() {
           try (Connection conn = dataSource.getConnection()) {
               return conn.isValid(2) ? "DB Connection Successful" : "DB Connection Failed";
           } catch (SQLException e) {
               return "DB Connection Error: " + e.getMessage();
           }
       }
   }
   ```

### 2. **ApplicationListener 사용**
   - `ApplicationListener`를 사용해 애플리케이션 시작 시점에서 DB 연결만 확인할 수 있습니다. 이 방법은 특정 이벤트(`ApplicationReadyEvent` 등)를 기반으로 동작하므로, `CommandLineRunner` 대신 특정 작업을 처리하는 데 유용합니다.
   ```java
   import org.springframework.boot.context.event.ApplicationReadyEvent;
   import org.springframework.context.ApplicationListener;
   import org.springframework.stereotype.Component;

   import javax.sql.DataSource;
   import java.sql.Connection;
   import java.sql.SQLException;

   @Component
   public class DatabaseConnectionChecker implements ApplicationListener<ApplicationReadyEvent> {
       private final DataSource dataSource;

       public DatabaseConnectionChecker(DataSource dataSource) {
           this.dataSource = dataSource;
       }

       @Override
       public void onApplicationEvent(ApplicationReadyEvent event) {
           try (Connection conn = dataSource.getConnection()) {
               System.out.println("DB Connection Successful: " + conn.isValid(2));
           } catch (SQLException e) {
               System.err.println("DB Connection Error: " + e.getMessage());
           }
       }
   }
   ```

### 3. **Health Indicator 활용**
   - Spring Boot의 `HealthIndicator`를 사용해 DB 연결 상태를 모니터링할 수 있습니다. Spring Boot Actuator를 사용 중이라면 `HealthIndicator`가 `/actuator/health` 엔드포인트로 노출되어 기본적인 연결 상태를 제공합니다.
   ```java
   import org.springframework.boot.actuate.health.Health;
   import org.springframework.boot.actuate.health.HealthIndicator;
   import org.springframework.stereotype.Component;

   import javax.sql.DataSource;
   import java.sql.Connection;
   import java.sql.SQLException;

   @Component
   public class DbHealthIndicator implements HealthIndicator {
       private final DataSource dataSource;

       public DbHealthIndicator(DataSource dataSource) {
           this.dataSource = dataSource;
       }

       @Override
       public Health health() {
           try (Connection conn = dataSource.getConnection()) {
               if (conn.isValid(2)) {
                   return Health.up().withDetail("DB Connection", "Successful").build();
               }
           } catch (SQLException e) {
               return Health.down().withDetail("DB Connection Error", e.getMessage()).build();
           }
           return Health.down().build();
       }
   }
   ```

### 4. **Lazy Initialization 및 자동 설정 제외 최적화**
   - DB 연결을 확인하는 데 필요한 최소 설정만 유지하고, 필요한 경우 REST API나 Health Check 방식으로 연결 상태를 확인할 수 있습니다.
   - `application.properties`:
     ```properties
     spring.main.lazy-initialization=true
     spring.datasource.hikari.maximum-pool-size=5
     ```

이와 같은 설정으로 `CommandLineRunner` 없이도 DB 연결 상태를 효율적으로 확인할 수 있으며, Spring Boot 애플리케이션을 초경량으로 구동할 수 있습니다.

네, `CommandLineRunner` 없이도 초경량으로 Spring Boot 애플리케이션을 기동할 수 있습니다. 이를 위해 불필요한 기능을 제외하고 DB 연결만 유지하는 설정을 적용할 수 있습니다. 아래와 같이 설정하면 기동 속도를 최적화할 수 있습니다.

### 1. **불필요한 자동 구성 제외하기**
   - Spring Boot의 자동 구성을 최소화하고, DB 연결만을 위한 필수 구성 요소만 남깁니다.
   - 예를 들어, Web, JPA, Security 등의 자동 구성을 제외합니다.
   ```java
   @SpringBootApplication(exclude = {
       WebMvcAutoConfiguration.class,
       SecurityAutoConfiguration.class,
       HibernateJpaAutoConfiguration.class
   })
   ```

### 2. **Lazy Initialization 활성화**
   - `spring.main.lazy-initialization=true` 설정을 통해 필요한 빈만 로딩하여 기동 시 불필요한 빈 생성을 줄입니다.
   ```properties
   spring.main.lazy-initialization=true
   ```

### 3. **HikariCP 최소 설정**
   - DB 연결 시 HikariCP 설정을 최적화하여, 적은 리소스로 빠르게 커넥션 풀을 관리할 수 있도록 합니다.
   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/yourdb
       username: yourusername
       password: yourpassword
       driver-class-name: com.mysql.cj.jdbc.Driver
       hikari:
         maximum-pool-size: 5              # 커넥션 풀 크기 최소화
         minimum-idle: 1                   # 최소 유휴 커넥션 수 최소화
         connection-timeout: 2000          # 커넥션 획득 대기 시간(ms)
         pool-name: "HikariPool"           # 풀 이름 설정
   ```

### 4. **Actuator 비활성화**
   - 애플리케이션에서 Actuator와 같은 모니터링 기능이 필요하지 않다면 비활성화하여 불필요한 빈 생성을 줄입니다.
   ```properties
   management.endpoints.enabled-by-default=false
   ```

### 5. **JPA 및 Hibernate 비활성화**
   - 단순 JDBC 기반으로만 DB에 접근한다면 JPA 및 Hibernate 설정을 비활성화합니다.
   ```properties
   spring.jpa.hibernate.ddl-auto=none
   spring.jpa.show-sql=false
   ```

### 6. **JVM 설정 최적화**
   - 가벼운 기동을 위해 JVM 메모리 설정을 조정하고, `Serial GC`와 같이 간단한 가비지 컬렉터를 설정합니다.
   - 예: `-Xms128m -Xmx128m -XX:+UseSerialGC`

이와 같이 설정하면 `CommandLineRunner` 없이도 DB 연결만을 위한 초경량 Spring Boot 애플리케이션을 구성할 수 있습니다.


네, `zeroground` 패키지를 사용하면 프로세스의 실행과 종료 시점을 보다 간단하게 제어할 수 있습니다. 이 패키지는 Java에서 다양한 시스템 작업을 관리할 수 있는 기능을 제공합니다. `zeroground` 패키지의 `ProcessWatcher` 클래스를 활용하면, 프로세스가 종료되는 시점을 손쉽게 감지할 수 있습니다.

아래는 `zeroground` 패키지의 `ProcessWatcher` 클래스를 사용하여 프로세스 종료 시점을 확인하는 예제입니다:

```java
import zeroground.process.ProcessWatcher;
import java.time.LocalDateTime;

public class ProcessExample {
    public static void main(String[] args) {
        try {
            // ProcessWatcher를 통해 커맨드 실행 및 종료 감지
            ProcessWatcher watcher = new ProcessWatcher("bash", "-c", "echo Hello, World! && sleep 3");

            // 프로세스 시작
            watcher.start();
            System.out.println("Process started...");

            // 종료 시점을 확인
            watcher.onExit((exitCode) -> {
                LocalDateTime endTime = LocalDateTime.now();
                System.out.println("Process exited at: " + endTime);
                System.out.println("Process exited with code: " + exitCode);

                if (exitCode == 0) {
                    System.out.println("Process completed successfully.");
                } else {
                    System.out.println("Process terminated with an error.");
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

### 코드 설명
1. **`ProcessWatcher`**: `ProcessWatcher` 객체는 지정된 명령을 실행하고, 프로세스 종료 시점에 대한 콜백을 제공합니다.
2. **프로세스 시작**: `watcher.start()`를 통해 프로세스를 시작합니다.
3. **종료 감지**: `watcher.onExit` 메서드에 종료 시 호출될 람다식 콜백을 등록하여 프로세스 종료 시점과 종료 코드를 확인합니다.

이 코드에서는 `zeroground` 패키지를 통해 종료 시점을 보다 직관적으로 관리할 수 있으며, 콜백을 사용해 종료 후 필요한 처리를 할 수 있습니다.
*/
