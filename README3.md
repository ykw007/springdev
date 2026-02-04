```xml
<dependencies>
    <!-- Spring Boot Batch -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-batch</artifactId>
    </dependency>
    
    <!-- Renjin -->
    <dependency>
        <groupId>org.renjin</groupId>
        <artifactId>renjin-script-engine</artifactId>
        <version>3.5-beta76</version>
    </dependency>
    
    <!-- Apache Commons Pool2 -->
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-pool2</artifactId>
    </dependency>
    
    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```
/////////////////////////////////////////////////////////////////////////////////
```yaml
spring:
  batch:
    job:
      enabled: true

# Renjin Pool 설정
renjin:
  pool:
    max-total: 30           # 최대 엔진 수
    min-idle: 10            # 최소 유지 엔진 수
    max-wait-millis: 30000  # 대기 최대 시간 (30초)

logging:
  level:
    com.example.batch: INFO
```
////////////////////////////////////////////////////////////////////////////////

```java
package com.example.batch.config.pool;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.renjin.script.RenjinScriptEngine;
import org.renjin.script.RenjinScriptEngineFactory;

import javax.script.ScriptException;

/**
 * RenjinScriptEngine 객체 풀을 위한 팩토리 클래스
 * 
 * <p>Apache Commons Pool2의 BasePooledObjectFactory를 확장하여
 * RenjinScriptEngine 인스턴스의 전체 생명주기(생성, 검증, 폐기)를 관리합니다.</p>
 * 
 * <p><b>생명주기 관리:</b></p>
 * <ul>
 *   <li><b>생성(create)</b>: 새로운 엔진 인스턴스 생성</li>
 *   <li><b>래핑(wrap)</b>: 엔진을 풀 관리 가능한 객체로 변환</li>
 *   <li><b>검증(validateObject)</b>: 엔진의 정상 작동 여부 확인</li>
 *   <li><b>폐기(destroyObject)</b>: 사용이 끝난 엔진 리소스 정리</li>
 * </ul>
 * 
 * <p><b>호출 시점:</b></p>
 * <ul>
 *   <li>create() - borrowObject() 호출 시 풀에 객체가 없을 때</li>
 *   <li>validateObject() - testWhileIdle=true일 때 주기적으로</li>
 *   <li>destroyObject() - 객체가 유휴 시간 초과 또는 검증 실패 시</li>
 * </ul>
 * 
 * @author Your Name
 * @since 1.0
 * @see BasePooledObjectFactory
 * @see RenjinScriptEngine
 */
@Slf4j
public class RenjinScriptEnginePoolFactory extends BasePooledObjectFactory<RenjinScriptEngine> {
    
    /**
     * Renjin 스크립트 엔진을 생성하는 팩토리
     * 
     * <p>RenjinScriptEngineFactory는 실제 R 인터프리터 인스턴스를 생성합니다.
     * 이 팩토리는 스레드 세이프하며, 여러 엔진 인스턴스를 생성할 수 있습니다.</p>
     */
    private final RenjinScriptEngineFactory engineFactory;
    
    /**
     * 생성자 - RenjinScriptEngineFactory 초기화
     * 
     * <p>팩토리 인스턴스를 한 번만 생성하여 재사용함으로써
     * 불필요한 초기화 오버헤드를 방지합니다.</p>
     */
    public RenjinScriptEnginePoolFactory() {
        this.engineFactory = new RenjinScriptEngineFactory();
    }
    
    /**
     * 새로운 RenjinScriptEngine 인스턴스를 생성합니다.
     * 
     * <p><b>호출 시점:</b></p>
     * <ul>
     *   <li>풀 초기화 시 (preparePool 호출 시 minIdle 개수만큼)</li>
     *   <li>borrowObject() 호출 시 사용 가능한 객체가 없고 maxTotal 미만일 때</li>
     *   <li>기존 객체가 검증 실패로 폐기되어 재생성이 필요할 때</li>
     * </ul>
     * 
     * <p><b>성능 고려사항:</b></p>
     * <ul>
     *   <li>RenjinScriptEngine 생성은 비용이 높은 작업입니다 (수백ms~수초)</li>
     *   <li>R 패키지 로딩, JVM 클래스 초기화 등이 포함됩니다</li>
     *   <li>따라서 풀링을 통한 재사용이 필수적입니다</li>
     * </ul>
     * 
     * @return 새로 생성된 RenjinScriptEngine 인스턴스
     * @throws Exception 엔진 생성 중 발생하는 모든 예외
     */
    @Override
    public RenjinScriptEngine create() throws Exception {
        log.debug("Creating new RenjinScriptEngine instance");
        
        // RenjinScriptEngineFactory를 통해 새 엔진 인스턴스 생성
        // 이 과정에서 R 런타임 환경이 초기화됩니다
        return engineFactory.getScriptEngine();
    }
    
    /**
     * RenjinScriptEngine을 PooledObject로 래핑합니다.
     * 
     * <p><b>역할:</b></p>
     * <ul>
     *   <li>순수 엔진 객체를 풀 관리 가능한 형태로 변환</li>
     *   <li>객체의 상태(사용 중/유휴), 생성 시간, 마지막 사용 시간 등을 추적</li>
     *   <li>풀의 내부 메타데이터 관리를 가능하게 함</li>
     * </ul>
     * 
     * <p><b>호출 시점:</b></p>
     * <ul>
     *   <li>create() 메서드가 새 객체를 생성한 직후</li>
     *   <li>풀이 객체를 관리 대상으로 등록할 때</li>
     * </ul>
     * 
     * <p>DefaultPooledObject는 Commons Pool2에서 제공하는 기본 구현체로,
     * 객체의 생명주기 정보를 자동으로 관리합니다.</p>
     * 
     * @param engine 래핑할 RenjinScriptEngine 인스턴스
     * @return 풀 관리 메타데이터를 포함하는 PooledObject
     */
    @Override
    public PooledObject<RenjinScriptEngine> wrap(RenjinScriptEngine engine) {
        // DefaultPooledObject는 다음 정보를 자동 추적합니다:
        // - 생성 시간 (createTime)
        // - 마지막 빌림 시간 (lastBorrowTime)
        // - 마지막 반환 시간 (lastReturnTime)
        // - 현재 상태 (IDLE, ALLOCATED, etc.)
        return new DefaultPooledObject<>(engine);
    }
    
    /**
     * 풀에서 관리 중인 RenjinScriptEngine의 유효성을 검증합니다.
     * 
     * <p><b>검증 전략:</b></p>
     * <ul>
     *   <li>간단한 R 표현식 "1 + 1"을 실행하여 엔진의 정상 작동 여부 확인</li>
     *   <li>결과가 2인지 확인하여 계산 정확성 검증</li>
     *   <li>빠른 검증을 위해 최소한의 계산만 수행</li>
     * </ul>
     * 
     * <p><b>호출 시점:</b></p>
     * <ul>
     *   <li><b>testWhileIdle=true</b>: 유휴 객체를 주기적으로 검증 (백그라운드)</li>
     *   <li><b>testOnBorrow=true</b>: borrowObject() 호출 시 대여 전 검증</li>
     *   <li><b>testOnReturn=true</b>: returnObject() 호출 시 반환 전 검증</li>
     * </ul>
     * 
     * <p><b>검증 실패 시:</b></p>
     * <ul>
     *   <li>해당 객체는 자동으로 풀에서 제거됩니다</li>
     *   <li>destroyObject()가 호출되어 리소스를 정리합니다</li>
     *   <li>필요 시 새 객체가 생성됩니다</li>
     * </ul>
     * 
     * <p><b>주의사항:</b></p>
     * <ul>
     *   <li>검증이 너무 복잡하면 성능에 영향을 줄 수 있습니다</li>
     *   <li>현재는 간단한 산술 연산으로 빠른 검증 수행</li>
     *   <li>메모리 누수나 상태 오염을 감지하기 위한 최소한의 테스트</li>
     * </ul>
     * 
     * @param pooledObject 검증할 PooledObject (엔진을 포함)
     * @return true: 엔진이 정상 작동, false: 엔진이 손상됨
     */
    @Override
    public boolean validateObject(PooledObject<RenjinScriptEngine> pooledObject) {
        try {
            // PooledObject에서 실제 엔진 인스턴스 추출
            RenjinScriptEngine engine = pooledObject.getObject();
            
            // 1 + 1 계산을 통한 기본 기능 검증
            // - R 파싱 엔진 정상 작동 확인
            // - 산술 연산 수행 가능 여부 확인
            // - 결과 반환 메커니즘 정상 여부 확인
            Object result = engine.eval("1 + 1");
            
            // 결과 검증:
            // 1. null이 아닌지 확인 (엔진이 정상적으로 계산 수행)
            // 2. Number 타입으로 캐스팅 가능한지 확인
            // 3. 정확한 계산 결과(2)인지 확인
            return result != null && ((Number) result).intValue() == 2;
            
        } catch (ScriptException e) {
            // R 스크립트 실행 오류 (문법 오류, 런타임 에러 등)
            log.error("RenjinScriptEngine validation failed", e);
            return false;
        } catch (Exception e) {
            // 예상치 못한 오류 (ClassCastException, NullPointerException 등)
            log.error("Unexpected error during RenjinScriptEngine validation", e);
            return false;
        }
    }
    
    /**
     * 사용이 끝난 RenjinScriptEngine을 폐기하고 리소스를 정리합니다.
     * 
     * <p><b>호출 시점:</b></p>
     * <ul>
     *   <li>객체가 검증(validateObject)에 실패했을 때</li>
     *   <li>유휴 시간이 minEvictableIdleTime을 초과했을 때</li>
     *   <li>풀의 크기를 줄여야 할 때 (maxIdle 초과)</li>
     *   <li>풀이 종료(close)될 때 모든 객체 정리</li>
     * </ul>
     * 
     * <p><b>현재 구현:</b></p>
     * <ul>
     *   <li>RenjinScriptEngine은 자체적으로 리소스 정리를 수행합니다</li>
     *   <li>명시적인 cleanup 메서드가 없으므로 로깅만 수행</li>
     *   <li>JVM의 가비지 컬렉터가 메모리를 회수합니다</li>
     * </ul>
     * 
     * <p><b>확장 가능성:</b></p>
     * <pre>
     * // 필요 시 명시적 정리 작업 추가 가능:
     * RenjinScriptEngine engine = pooledObject.getObject();
     * 
     * // 1. 로드된 R 패키지 언로드
     * engine.eval("detach('package:somePackage', unload=TRUE)");
     * 
     * // 2. 글로벌 변수 정리
     * engine.eval("rm(list=ls())");
     * 
     * // 3. 외부 연결 종료
     * engine.eval("closeAllConnections()");
     * </pre>
     * 
     * @param pooledObject 폐기할 PooledObject
     * @throws Exception 리소스 정리 중 발생하는 예외
     */
    @Override
    public void destroyObject(PooledObject<RenjinScriptEngine> pooledObject) throws Exception {
        log.debug("Destroying RenjinScriptEngine");
        
        // 현재는 로깅만 수행하지만, 필요 시 다음 작업을 추가할 수 있습니다:
        // - R 환경 정리 (rm(list=ls()))
        // - 열린 파일 핸들 닫기
        // - 데이터베이스 연결 종료
        // - 임시 파일 삭제
        
        // RenjinScriptEngine 자체는 특별한 cleanup이 필요하지 않으며,
        // GC에 의해 자동으로 메모리가 회수됩니다.
    }
}
```

//////////////////////////////////////////////////////////////////////

```java
/**
 * Renjin Script Engine 객체 풀 설정 클래스
 * 
 * <p>RenjinScriptEngine은 초기화 비용이 높기 때문에 객체 풀링을 통해
 * 재사용하여 성능을 최적화합니다. Apache Commons Pool2를 사용하여
 * 엔진 인스턴스의 생성, 재사용, 폐기를 관리합니다.</p>
 * 
 * @author Your Name
 * @since 1.0
 */
@Slf4j
@Configuration
public class RenjinEnginePoolConfig {
    
    /**
     * 풀에서 관리할 수 있는 최대 객체 수
     * 기본값: 30개
     * 
     * <p>동시에 처리할 수 있는 R 스크립트 실행 요청의 최대 개수를 의미합니다.
     * 시스템의 CPU 코어 수와 메모리, 예상 동시 요청 수를 고려하여 설정합니다.</p>
     */
    @Value("${renjin.pool.max-total:30}")
    private int maxTotal;
    
    /**
     * 풀에 항상 유지할 최소 유휴 객체 수
     * 기본값: 10개
     * 
     * <p>요청이 없을 때도 이 개수만큼 사전 생성된 엔진을 유지하여
     * 즉시 응답 가능하도록 합니다. 초기 로딩 시간을 줄이는 역할을 합니다.</p>
     */
    @Value("${renjin.pool.min-idle:10}")
    private int minIdle;
    
    /**
     * 풀에서 객체를 빌릴 때 대기할 최대 시간 (밀리초)
     * 기본값: 30000ms (30초)
     * 
     * <p>모든 객체가 사용 중일 때 새로운 요청이 대기할 수 있는 최대 시간입니다.
     * 이 시간이 초과되면 NoSuchElementException이 발생합니다.</p>
     */
    @Value("${renjin.pool.max-wait-millis:30000}")
    private long maxWaitMillis;
    
    /**
     * Renjin Engine Pool의 설정 객체를 생성하는 Bean
     * 
     * @return 설정이 완료된 GenericObjectPoolConfig 인스턴스
     */
    @Bean
    public GenericObjectPoolConfig<RenjinScriptEngine> renjinPoolConfig() {
        GenericObjectPoolConfig<RenjinScriptEngine> config = new GenericObjectPoolConfig<>();
        
        // ========== 필수 용량 설정 ==========
        
        /**
         * setMaxTotal: 풀이 관리할 수 있는 최대 객체 수
         * - 이 값을 초과하는 객체는 생성되지 않음
         * - 동시 처리 가능한 최대 R 스크립트 실행 수를 결정
         */
        config.setMaxTotal(maxTotal);
        
        /**
         * setMaxIdle: 풀에 유지할 수 있는 최대 유휴 객체 수
         * - maxTotal과 동일하게 설정하여 생성된 모든 객체를 유지
         * - 객체 재생성 비용을 최소화
         */
        config.setMaxIdle(maxTotal);
        
        /**
         * setMinIdle: 풀에 항상 유지할 최소 유휴 객체 수
         * - 이 개수만큼 사전 생성하여 즉시 사용 가능하도록 함
         * - Cold Start 문제 해결
         */
        config.setMinIdle(minIdle);
        
        /**
         * setMaxWait: 객체를 빌릴 때 최대 대기 시간
         * - 풀이 고갈되었을 때 블로킹되는 최대 시간
         * - 타임아웃 발생 시 예외 처리 필요
         */
        config.setMaxWait(Duration.ofMillis(maxWaitMillis));
        
        // ========== 객체 검증 및 관리 설정 ==========
        
        /**
         * setTestWhileIdle: 유휴 객체의 유효성 검사 활성화
         * - 유휴 상태의 객체를 주기적으로 검증
         * - 손상된 엔진 인스턴스를 사전에 제거
         * - PooledObjectFactory.validateObject() 메서드 호출
         */
        config.setTestWhileIdle(true);
        
        /**
         * setTimeBetweenEvictionRuns: 유휴 객체 제거 스레드 실행 주기
         * - 1분마다 풀을 검사하여 오래된 객체를 정리
         * - MinEvictableIdleTime과 함께 작동
         * - -1로 설정 시 제거 스레드가 실행되지 않음
         */
        config.setTimeBetweenEvictionRuns(Duration.ofMinutes(1));
        
        /**
         * setMinEvictableIdleTime: 객체가 제거 대상이 되는 최소 유휴 시간
         * - 5분 이상 사용되지 않은 객체는 제거 후보
         * - MinIdle 수보다 많은 객체만 제거됨
         * - 메모리 효율성과 객체 재사용의 균형을 맞춤
         */
        config.setMinEvictableIdleTime(Duration.ofMinutes(5));
        
        /**
         * setBlockWhenExhausted: 풀이 고갈되었을 때 블로킹 여부
         * - true: 객체가 반환될 때까지 대기 (maxWaitMillis만큼)
         * - false: 즉시 예외 발생
         * - 안정적인 처리를 위해 true 권장
         */
        config.setBlockWhenExhausted(true);
        
        /**
         * setLifo: Last In First Out (후입선출) 방식 설정
         * - true: 가장 최근에 반환된 객체를 먼저 사용 (스택 방식)
         * - false: 가장 오래 전에 반환된 객체를 먼저 사용 (큐 방식)
         * - LIFO는 캐시 효율성이 높아 일반적으로 권장
         */
        config.setLifo(true);
        
        /**
         * setJmxEnabled: JMX를 통한 모니터링 비활성화
         * - false: JMX MBean 등록 안 함 (오버헤드 감소)
         * - true: JMX로 실시간 풀 상태 모니터링 가능
         * - 프로덕션 환경에서는 필요 시 활성화
         */
        config.setJmxEnabled(false);
        
        // 설정 완료 로그 출력
        log.info("Renjin Pool Config - MaxTotal: {}, MinIdle: {}, MaxWait: {}ms",
                 maxTotal, minIdle, maxWaitMillis);
        
        return config;
    }
    
    /**
     * Renjin Script Engine 객체 풀을 생성하는 Bean
     * 
     * <p>실제 RenjinScriptEngine 인스턴스들을 관리하는 풀을 생성하고,
     * 초기화 시 MinIdle 개수만큼 사전 생성(pre-warming)합니다.</p>
     * 
     * @param poolConfig 풀 설정 객체
     * @return 초기화된 RenjinScriptEngine 객체 풀
     */
    @Bean(destroyMethod = "close")  // 애플리케이션 종료 시 자동으로 close() 호출
    public GenericObjectPool<RenjinScriptEngine> renjinEnginePool(
            GenericObjectPoolConfig<RenjinScriptEngine> poolConfig) {
        
        log.info("Initializing Renjin Engine Pool");
        long startTime = System.currentTimeMillis();
        
        /**
         * RenjinScriptEnginePoolFactory: 실제 엔진 생성/검증/폐기를 담당
         * - makeObject(): 새 RenjinScriptEngine 인스턴스 생성
         * - validateObject(): 엔진의 유효성 검증
         * - destroyObject(): 엔진 인스턴스 정리
         * - activateObject(): 대여 전 활성화
         * - passivateObject(): 반환 전 비활성화
         */
        RenjinScriptEnginePoolFactory factory = new RenjinScriptEnginePoolFactory();
        
        /**
         * GenericObjectPool 생성
         * - factory: 객체의 생명주기를 관리하는 팩토리
         * - poolConfig: 풀의 동작 방식을 정의하는 설정
         */
        GenericObjectPool<RenjinScriptEngine> pool = new GenericObjectPool<>(factory, poolConfig);
        
        /**
         * Pre-warming (사전 로딩)
         * - 애플리케이션 시작 시 MinIdle 개수만큼 엔진을 미리 생성
         * - 첫 요청의 응답 시간을 개선 (Cold Start 방지)
         * - 생성 실패 시에도 애플리케이션은 정상 시작
         */
        try {
            pool.preparePool();  // MinIdle 개수만큼 객체 생성 시도
            log.info("Renjin Engine Pool pre-warmed with {} instances", minIdle);
        } catch (Exception e) {
            // Pre-warming 실패는 치명적이지 않음 (필요 시 런타임에 생성됨)
            log.warn("Failed to pre-warm Renjin Engine Pool", e);
        }
        
        // 초기화 소요 시간 로깅 (성능 모니터링용)
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Renjin Engine Pool initialized in {}ms", elapsed);
        
        return pool;
    }
}

```
//////////////////////////////////////////////////////////////////////

```java
package com.example.batch.config.pool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.renjin.script.RenjinScriptEngine;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RenjinEnginePoolManager {
    
    private final GenericObjectPool<RenjinScriptEngine> enginePool;
    
    /**
     * 풀에서 Renjin 엔진을 가져옵니다.
     */
    public RenjinScriptEngine borrowEngine() throws Exception {
        log.debug("Borrowing RenjinScriptEngine. Active: {}, Idle: {}",
                  enginePool.getNumActive(), enginePool.getNumIdle());
        
        RenjinScriptEngine engine = enginePool.borrowObject();
        
        log.debug("RenjinScriptEngine borrowed. Active: {}, Idle: {}",
                  enginePool.getNumActive(), enginePool.getNumIdle());
        
        return engine;
    }
    
    /**
     * 엔진을 풀에 반환합니다.
     */
    public void returnEngine(RenjinScriptEngine engine) {
        if (engine == null) {
            log.warn("Attempted to return null engine");
            return;
        }
        
        enginePool.returnObject(engine);
        log.debug("RenjinScriptEngine returned. Active: {}, Idle: {}",
                  enginePool.getNumActive(), enginePool.getNumIdle());
    }
    
    /**
     * 손상된 엔진을 풀에서 제거합니다.
     */
    public void invalidateEngine(RenjinScriptEngine engine) {
        if (engine == null) {
            return;
        }
        
        try {
            log.warn("Invalidating RenjinScriptEngine");
            enginePool.invalidateObject(engine);
        } catch (Exception e) {
            log.error("Error invalidating engine", e);
        }
    }
    
    /**
     * 풀 상태 조회
     */
    public PoolStatus getStatus() {
        return new PoolStatus(
            enginePool.getMaxTotal(),
            enginePool.getNumActive(),
            enginePool.getNumIdle(),
            enginePool.getNumWaiters()
        );
    }
    
    /**
     * 풀 상태 정보
     */
    public static class PoolStatus {
        private final int maxTotal;
        private final int numActive;
        private final int numIdle;
        private final int numWaiters;
        
        public PoolStatus(int maxTotal, int numActive, int numIdle, int numWaiters) {
            this.maxTotal = maxTotal;
            this.numActive = numActive;
            this.numIdle = numIdle;
            this.numWaiters = numWaiters;
        }
        
        public int getMaxTotal() { return maxTotal; }
        public int getNumActive() { return numActive; }
        public int getNumIdle() { return numIdle; }
        public int getNumWaiters() { return numWaiters; }
        
        public double getUtilizationRate() {
            return maxTotal > 0 ? (numActive * 100.0 / maxTotal) : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "PoolStatus[maxTotal=%d, active=%d, idle=%d, waiters=%d, utilization=%.2f%%]",
                maxTotal, numActive, numIdle, numWaiters, getUtilizationRate()
            );
        }
    }
}
```

/////////////////////////////////////////////////////////////////////

```java
package com.example.batch.service;

import com.example.batch.config.pool.RenjinEnginePoolManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.renjin.script.RenjinScriptEngine;
import org.springframework.stereotype.Service;

import javax.script.ScriptException;

@Slf4j
@Service
@RequiredArgsConstructor
public class EcoAnalysisService {
    
    private final RenjinEnginePoolManager poolManager;
    
    /**
     * Wilcoxon test를 수행하여 p-value를 반환합니다.
     */
    public Double performWilcoxonTest(double[] experimentGroup, double[] controlGroup) {
        RenjinScriptEngine engine = null;
        
        try {
            engine = poolManager.borrowEngine();
            return executeWilcoxTest(engine, experimentGroup, controlGroup);
            
        } catch (Exception e) {
            log.error("Error during Wilcoxon test execution", e);
            
            if (engine != null) {
                poolManager.invalidateEngine(engine);
                engine = null;
            }
            
            throw new RuntimeException("Wilcoxon test failed", e);
            
        } finally {
            if (engine != null) {
                poolManager.returnEngine(engine);
            }
        }
    }
    
    private Double executeWilcoxTest(RenjinScriptEngine engine,
                                     double[] experimentGroup,
                                     double[] controlGroup) throws ScriptException {
        
        engine.put("experiment", experimentGroup);
        engine.put("control", controlGroup);
        
        String script = "result <- wilcox.test(experiment, control, exact=FALSE); result$p.value";
        Object result = engine.eval(script);
        
        if (result == null) {
            log.warn("Wilcoxon test returned null result");
            return null;
        }
        
        return ((Number) result).doubleValue();
    }
    
    public RenjinEnginePoolManager.PoolStatus getPoolStatus() {
        return poolManager.getStatus();
    }
}
```
