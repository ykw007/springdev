# Renjin 풀 기반 Executor 사용을 위한 코드 정리

당신의 패턴을 기반으로 **안전하고 간단한** 코드로 정리하겠습니다.

## 1. 간단한 래퍼 서비스 (핵심)

```java
package com.example.batch.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.renjin.script.RenjinScriptEngine;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RenjinExecutorService {
    
    private final GenericObjectPool<RenjinScriptEngine> pool;
    private final ExecutorService executor;
    
    /**
     * ✅ 간단한 패턴: 풀에서 엔진 획득 후 Executor에서 실행
     * 
     * 사용 예:
     * RenjinScriptEngine engine = pool.borrowObject();
     * executor.submit(() -> {
     *     try {
     *         Object result = engine.eval("2 + 2");
     *     } finally {
     *         pool.returnObject(engine);
     *     }
     * });
     */
    public Future<Object> execute(String script) {
        RenjinScriptEngine engine = null;
        
        try {
            // 1️⃣ 풀에서 엔진 획득
            engine = pool.borrowObject();
            final RenjinScriptEngine finalEngine = engine;
            
            // 2️⃣ Executor에서 실행
            return executor.submit(() -> {
                try {
                    // 3️⃣ 스크립트 실행
                    Object result = finalEngine.eval(script);
                    log.debug("Script executed: {}", script);
                    return result;
                    
                } catch (Exception e) {
                    log.error("Script execution error: {}", script, e);
                    throw new RuntimeException("Execution failed", e);
                    
                } finally {
                    // 4️⃣ 풀에 반환
                    pool.returnObject(finalEngine);
                }
            });
            
        } catch (Exception e) {
            log.error("Failed to borrow engine from pool", e);
            if (engine != null) {
                pool.returnObject(engine);
            }
            throw new RuntimeException("Failed to execute script", e);
        }
    }
    
    /**
     * ✅ 변수 포함 실행
     */
    public Future<Object> executeWithVariables(String script, java.util.Map<String, Object> variables) {
        RenjinScriptEngine engine = null;
        
        try {
            engine = pool.borrowObject();
            final RenjinScriptEngine finalEngine = engine;
            
            return executor.submit(() -> {
                try {
                    // 변수 설정
                    for (java.util.Map.Entry<String, Object> entry : variables.entrySet()) {
                        finalEngine.put(entry.getKey(), entry.getValue());
                    }
                    
                    // 스크립트 실행
                    Object result = finalEngine.eval(script);
                    log.debug("Script with variables executed");
                    return result;
                    
                } catch (Exception e) {
                    log.error("Script execution error", e);
                    throw new RuntimeException("Execution failed", e);
                    
                } finally {
                    pool.returnObject(finalEngine);
                }
            });
            
        } catch (Exception e) {
            log.error("Failed to borrow engine", e);
            if (engine != null) {
                pool.returnObject(engine);
            }
            throw new RuntimeException("Failed to execute", e);
        }
    }
    
    /**
     * ✅ 동기 실행 (결과 대기)
     */
    public Object executeSync(String script) throws Exception {
        Future<Object> future = execute(script);
        return future.get(30, TimeUnit.SECONDS);
    }
    
    /**
     * ✅ 동기 실행 (타임아웃 지정)
     */
    public Object executeSyncWithTimeout(String script, long timeoutSeconds) throws Exception {
        Future<Object> future = execute(script);
        return future.get(timeoutSeconds, TimeUnit.SECONDS);
    }
}
```

## 2. Spring Boot 설정

```java
package com.example.batch.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.renjin.script.RenjinScriptEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.example.batch.config.pool.RenjinScriptEnginePoolFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Configuration
public class RenjinPoolConfig {
    
    @Value("${renjin.pool.max-total:30}")
    private int maxTotal;
    
    @Value("${renjin.pool.max-idle:30}")
    private int maxIdle;
    
    @Value("${renjin.pool.min-idle:10}")
    private int minIdle;
    
    @Value("${renjin.pool.max-wait-millis:30000}")
    private long maxWaitMillis;
    
    /**
     * Renjin 엔진 풀 설정
     */
    @Bean
    public GenericObjectPoolConfig<RenjinScriptEngine> poolConfig() {
        GenericObjectPoolConfig<RenjinScriptEngine> config = 
            new GenericObjectPoolConfig<>();
        
        config.setMaxTotal(maxTotal);
        config.setMaxIdle(maxIdle);
        config.setMinIdle(minIdle);
        config.setMaxWait(java.time.Duration.ofMillis(maxWaitMillis));
        config.setTestWhileIdle(true);
        config.setTimeBetweenEvictionRuns(java.time.Duration.ofMinutes(1));
        config.setMinEvictableIdleTime(java.time.Duration.ofMinutes(5));
        
        log.info("Renjin Pool Config: maxTotal={}, maxIdle={}, minIdle={}", 
                 maxTotal, maxIdle, minIdle);
        
        return config;
    }
    
    /**
     * Renjin 엔진 풀
     */
    @Bean(destroyMethod = "close")
    public GenericObjectPool<RenjinScriptEngine> renjinEnginePool(
            GenericObjectPoolConfig<RenjinScriptEngine> poolConfig) {
        
        GenericObjectPool<RenjinScriptEngine> pool = 
            new GenericObjectPool<>(
                new RenjinScriptEnginePoolFactory(), 
                poolConfig
            );
        
        log.info("Renjin Engine Pool initialized");
        return pool;
    }
    
    /**
     * Executor Service (CPU 코어 수만큼)
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService renjinExecutor() {
        int coreCount = Runtime.getRuntime().availableProcessors();
        
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger(0);
            
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("RenjinExecutor-" + count.incrementAndGet());
                thread.setDaemon(false);
                return thread;
            }
        };
        
        ExecutorService executor = Executors.newFixedThreadPool(coreCount, threadFactory);
        
        log.info("Renjin Executor Service initialized with {} threads", coreCount);
        return executor;
    }
}
```
