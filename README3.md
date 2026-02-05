# Renjin 엔진 + Apache Commons Pool2 멀티스레드 처리 구현

Renjin 엔진을 Apache Commons Pool2로 pooling하고 멀티스레드 처리하는 완전한 구현 예제를 제공하겠습니다.

## 1️⃣ Maven 의존성 추가

```xml
<dependencies>
    <!-- Renjin -->
    <dependency>
        <groupId>org.renjin</groupId>
        <artifactId>renjin-script-engine</artifactId>
        <version>0.9.2749</version>
    </dependency>
    
    <!-- Apache Commons Pool2 -->
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-pool2</artifactId>
        <version>2.11.1</version>
    </dependency>
    
    <!-- Logging -->
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
        <version>2.0.5</version>
    </dependency>
    <dependency>
        <groupId>ch.qos.logback</groupId>
        <artifactId>logback-classic</artifactId>
        <version>1.4.6</version>
    </dependency>
</dependencies>
```

## 2️⃣ Renjin 엔진 Pool Factory 구현

```java
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renjin ScriptEngine을 위한 Object Factory
 */
public class RenjinEngineFactory extends BasePooledObjectFactory<ScriptEngine> {
    private static final Logger logger = LoggerFactory.getLogger(RenjinEngineFactory.class);
    private final ScriptEngineManager manager;

    public RenjinEngineFactory() {
        this.manager = new ScriptEngineManager();
    }

    @Override
    public ScriptEngine create() throws Exception {
        logger.debug("새로운 Renjin 엔진 인스턴스 생성");
        ScriptEngine engine = manager.getEngineByName("Renjin");
        if (engine == null) {
            throw new RuntimeException("Renjin 엔진을 찾을 수 없습니다");
        }
        return engine;
    }

    @Override
    public PooledObject<ScriptEngine> wrap(ScriptEngine engine) {
        return new DefaultPooledObject<>(engine);
    }

    @Override
    public void destroyObject(PooledObject<ScriptEngine> pooledObject) {
        logger.debug("Renjin 엔진 인스턴스 제거");
        // Renjin 엔진은 특별한 정리가 필요 없음
    }

    @Override
    public boolean validateObject(PooledObject<ScriptEngine> pooledObject) {
        // 간단한 유효성 검사
        return pooledObject.getObject() != null;
    }
}
```

## 3️⃣ Renjin Pool Manager 구현

```java
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renjin ScriptEngine Pool 관리자
 */
public class RenjinEnginePool {
    private static final Logger logger = LoggerFactory.getLogger(RenjinEnginePool.class);
    private final ObjectPool<ScriptEngine> pool;
    private static volatile RenjinEnginePool instance;

    private RenjinEnginePool(int maxPoolSize) {
        GenericObjectPoolConfig<ScriptEngine> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(maxPoolSize);                          // 최대 풀 크기
        config.setMaxIdle(maxPoolSize / 2);                       // 최대 유휴 엔진
        config.setMinIdle(2);                                      // 최소 유휴 엔진
        config.setTestOnBorrow(true);                             // 사용 전 검증
        config.setTestOnReturn(true);                             // 반환 전 검증
        config.setMaxWaitMillis(5000);                            // 최대 대기 시간
        config.setBlockWhenExhausted(true);                       // 고갈시 대기

        this.pool = new GenericObjectPool<>(new RenjinEngineFactory(), config);
        logger.info("Renjin 엔진 풀 생성 (최대 크기: {})", maxPoolSize);
    }

    /**
     * 싱글톤 인스턴스 획득
     */
    public static RenjinEnginePool getInstance(int maxPoolSize) {
        if (instance == null) {
            synchronized (RenjinEnginePool.class) {
                if (instance == null) {
                    instance = new RenjinEnginePool(maxPoolSize);
                }
            }
        }
        return instance;
    }

    /**
     * 풀에서 엔진 획득
     */
    public ScriptEngine borrowEngine() throws Exception {
        return pool.borrowObject();
    }

    /**
     * 풀에 엔진 반환
     */
    public void returnEngine(ScriptEngine engine) {
        if (engine != null) {
            try {
                pool.returnObject(engine);
            } catch (Exception e) {
                logger.error("엔진 반환 실패", e);
            }
        }
    }

    /**
     * 엔진 무효화
     */
    public void invalidateEngine(ScriptEngine engine) {
        if (engine != null) {
            try {
                pool.invalidateObject(engine);
            } catch (Exception e) {
                logger.error("엔진 무효화 실패", e);
            }
        }
    }

    /**
     * 풀 종료
     */
    public void close() {
        try {
            pool.close();
            logger.info("Renjin 엔진 풀 종료");
        } catch (Exception e) {
            logger.error("풀 종료 실패", e);
        }
    }

    public int getActiveCount() {
        return pool.getNumActive();
    }

    public int getIdleCount() {
        return pool.getNumIdle();
    }
}
```

## 4️⃣ 멀티스레드 작업 처리 클래스

```java
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renjin 멀티스레드 작업 실행자
 */
public class RenjinTaskExecutor {
    private static final Logger logger = LoggerFactory.getLogger(RenjinTaskExecutor.class);
    private final RenjinEnginePool pool;
    private final ExecutorService executorService;
    private final int threadPoolSize;

    public RenjinTaskExecutor(int threadPoolSize, int enginePoolSize) {
        this.threadPoolSize = threadPoolSize;
        this.pool = RenjinEnginePool.getInstance(enginePoolSize);
        
        // CPU 코어 기반 스레드 풀 생성
        this.executorService = Executors.newFixedThreadPool(
            threadPoolSize,
            new ThreadFactory() {
                private final AtomicInteger count = new AtomicInteger(1);
                
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r);
                    t.setName("Renjin-Worker-" + count.getAndIncrement());
                    t.setDaemon(false);
                    return t;
                }
            }
        );
        
        logger.info("RenjinTaskExecutor 초기화 (스레드: {}, 엔진 풀: {})", 
                   threadPoolSize, enginePoolSize);
    }

    /**
     * R 스크립트 비동기 실행
     */
    public Future<Object> executeAsync(String rScript) {
        return executorService.submit(() -> {
            ScriptEngine engine = null;
            try {
                engine = pool.borrowEngine();
                logger.debug("[{}] R 스크립트 실행 시작", Thread.currentThread().getName());
                
                Object result = engine.eval(rScript);
                
                logger.debug("[{}] R 스크립트 실행 완료", Thread.currentThread().getName());
                return result;
            } catch (ScriptException e) {
                logger.error("R 스크립트 실행 오류", e);
                if (engine != null) {
                    pool.invalidateEngine(engine);
                }
                throw new RuntimeException("R 스크립트 실행 실패", e);
            } finally {
                pool.returnEngine(engine);
            }
        });
    }

    /**
     * 배치 작업 실행
     */
    public CompletableFuture<java.util.List<Object>> executeBatch(java.util.List<String> scripts) {
        java.util.List<CompletableFuture<Object>> futures = new java.util.ArrayList<>();
        
        for (String script : scripts) {
            futures.add(executeAsync(script).thenApply(r -> r));
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(f -> {
                    try {
                        return f.get();
                    } catch (Exception e) {
                        logger.error("배치 작업 오류", e);
                        return null;
                    }
                })
                .collect(java.util.stream.Collectors.toList()));
    }

    /**
     * 동기식 실행 (타임아웃 포함)
     */
    public Object executeSynchronous(String rScript, long timeoutSeconds) 
            throws TimeoutException, ExecutionException, InterruptedException {
        Future<Object> future = executeAsync(rScript);
        return future.get(timeoutSeconds, TimeUnit.SECONDS);
    }

    /**
     * 풀 상태 조회
     */
    public void printPoolStatus() {
        logger.info("=== 풀 상태 ===");
        logger.info("활성 엔진: {}", pool.getActiveCount());
        logger.info("유휴 엔진: {}", pool.getIdleCount());
    }

    /**
     * 리소스 정리
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        pool.close();
        logger.info("RenjinTaskExecutor 종료");
    }
}
```

## 5️⃣ 사용 예제

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class RenjinExample {
    public static void main(String[] args) throws Exception {
        // CPU 코어 수 기반 스레드 풀 크기 설정
        int cpuCores = Runtime.getRuntime().availableProcessors();
        int threadPoolSize = cpuCores * 2;  // 하이퍼스레딩 고려
        int enginePoolSize = threadPoolSize + 5;
        
        RenjinTaskExecutor executor = new RenjinTaskExecutor(threadPoolSize, enginePoolSize);

        try {
            // 예제 1: 단일 비동기 작업
            System.out.println("\n=== 예제 1: 단일 비동기 작업 ===");
            var future1 = executor.executeAsync("x <- c(1, 2, 3, 4, 5); sum(x)");
            System.out.println("결과: " + future1.get());
            executor.printPoolStatus();

            // 예제 2: 동기식 실행 (타임아웃 포함)
            System.out.println("\n=== 예제 2: 동기식 실행 ===");
            Object result = executor.executeSynchronous(
                "mean(c(10, 20, 30, 40, 50))", 
                5  // 5초 타임아웃
            );
            System.out.println("결과: " + result);

            // 예제 3: 배치 작업
            System.out.println("\n=== 예제 3: 배치 작업 ===");
            List<String> scripts = new ArrayList<>();
            for (int i = 1; i <= cpuCores; i++) {
                scripts.add("sum(1:" + (i * 100) + ")");
            }
            
            long startTime = System.currentTimeMillis();
            CompletableFuture<List<Object>> batchResults = executor.executeBatch(scripts);
            List<Object> results = batchResults.get();
            long endTime = System.currentTimeMillis();
            
            System.out.println("배치 결과 개수: " + results.size());
            for (int i = 0; i < results.size(); i++) {
                System.out.println("결과 [" + i + "]: " + results.get(i));
            }
            System.out.println("실행 시간: " + (endTime - startTime) + "ms");
            executor.printPoolStatus();

            // 예제 4: 복잡한 R 계산
            System.out.println("\n=== 예제 4: 복잡한 R 계산 ===");
            String complexScript = 
                "data <- rnorm(10000, mean=100, sd=15);\n" +
                "list(\n" +
                "  mean = mean(data),\n" +
                "  sd = sd(data),\n" +
                "  median = median(data)\n" +
                ")";
            
            Object complexResult = executor.executeSynchronous(complexScript, 10);
            System.out.println("복잡한 계산 결과: " + complexResult);

        } catch (TimeoutException e) {
            System.err.println("실행 타임아웃: " + e.getMessage());
        } catch (ExecutionException e) {
            System.err.println("실행 오류: " + e.getCause().getMessage());
        } finally {
            executor.shutdown();
        }
    }
}
```
