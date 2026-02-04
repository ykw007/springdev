///////////////////////////////////////////////////////

```yaml
spring:
  batch:
    job:
      enabled: true

# Renjin 설정
renjin:
  pool:
    size: 30
    acquire-timeout-seconds: 30
```

////////////////////////////////////////////////////////////////

```java
package com.example.batch.config;

import org.renjin.script.RenjinScriptEngine;
import org.renjin.script.RenjinScriptEngineFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RenjinEnginePool {
    
    @Value("${renjin.pool.size:30}")
    private int poolSize;
    
    @Value("${renjin.pool.acquire-timeout-seconds:30}")
    private int acquireTimeoutSeconds;
    
    private BlockingQueue<RenjinScriptEngine> enginePool;
    private final RenjinScriptEngineFactory factory;
    
    public RenjinEnginePool() {
        this.factory = new RenjinScriptEngineFactory();
    }
    
    @PostConstruct
    public void initialize() {
        // 설정 검증
        validateConfiguration();
        
        // 풀 초기화
        this.enginePool = new LinkedBlockingQueue<>(poolSize);
        
        log.info("Initializing Renjin Engine Pool with size: {}, timeout: {}s", 
                 poolSize, acquireTimeoutSeconds);
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < poolSize; i++) {
            try {
                RenjinScriptEngine engine = factory.getScriptEngine();
                enginePool.offer(engine);
                
                if ((i + 1) % 10 == 0 || (i + 1) == poolSize) {
                    log.info("Created {}/{} Renjin engines", i + 1, poolSize);
                }
                
            } catch (Exception e) {
                log.error("Failed to create Renjin engine {}/{}", i + 1, poolSize, e);
                throw new RuntimeException("Failed to initialize Renjin engine pool", e);
            }
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Renjin Engine Pool initialized successfully. Pool size: {}, Timeout: {}s, Took: {}ms", 
                 poolSize, acquireTimeoutSeconds, elapsed);
    }
    
    /**
     * 풀에서 Renjin 엔진을 가져옵니다.
     * 타임아웃 시간 내에 사용 가능한 엔진이 없으면 예외 발생
     */
    public RenjinScriptEngine acquire() throws InterruptedException {
        log.debug("Attempting to acquire Renjin engine. Available: {}/{}", 
                  enginePool.size(), poolSize);
        
        RenjinScriptEngine engine = enginePool.poll(acquireTimeoutSeconds, TimeUnit.SECONDS);
        
        if (engine == null) {
            log.error("Failed to acquire Renjin engine within {} seconds. Pool exhausted!", 
                      acquireTimeoutSeconds);
            throw new RuntimeException(
                String.format("Renjin engine pool exhausted (timeout: %ds)", acquireTimeoutSeconds));
        }
        
        log.debug("Renjin engine acquired. Remaining: {}/{}", enginePool.size(), poolSize);
        return engine;
    }
    
    /**
     * 사용한 엔진을 풀에 반환합니다.
     */
    public void release(RenjinScriptEngine engine) {
        if (engine == null) {
            log.warn("Attempted to release null engine");
            return;
        }
        
        boolean returned = enginePool.offer(engine);
        if (returned) {
            log.debug("Renjin engine released. Available: {}/{}", enginePool.size(), poolSize);
        } else {
            log.error("Failed to return engine to pool. Pool might be full!");
        }
    }
    
    /**
     * 풀의 현재 상태 정보를 반환합니다.
     */
    public PoolStatus getStatus() {
        int available = enginePool.size();
        int inUse = poolSize - available;
        return new PoolStatus(poolSize, available, inUse, acquireTimeoutSeconds);
    }
    
    /**
     * 설정된 풀 크기를 반환합니다.
     */
    public int getPoolSize() {
        return poolSize;
    }
    
    /**
     * 설정된 타임아웃 시간을 반환합니다.
     */
    public int getAcquireTimeoutSeconds() {
        return acquireTimeoutSeconds;
    }
    
    @PreDestroy
    public void destroy() {
        log.info("Destroying Renjin Engine Pool");
        enginePool.clear();
        log.info("Renjin Engine Pool destroyed");
    }
    
    public static class PoolStatus {
        private final int totalSize;
        private final int available;
        private final int inUse;
        private final int timeoutSeconds;
        
        public PoolStatus(int totalSize, int available, int inUse, int timeoutSeconds) {
            this.totalSize = totalSize;
            this.available = available;
            this.inUse = inUse;
            this.timeoutSeconds = timeoutSeconds;
        }
        
        public int getTotalSize() { 
            return totalSize; 
        }
        
        public int getAvailable() { 
            return available; 
        }
        
        public int getInUse() { 
            return inUse; 
        }
        
        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }
        
        public double getUtilizationRate() {
            return totalSize > 0 ? (inUse * 100.0 / totalSize) : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("RenjinPool[total=%d, available=%d, inUse=%d, utilization=%.2f%%, timeout=%ds]", 
                               totalSize, available, inUse, getUtilizationRate(), timeoutSeconds);
        }
    }
}

    
//////////////////////////////////
    public Double performWilcoxonTest(double[] experimentGroup, double[] controlGroup) {
        RenjinScriptEngine engine = null;
        
        try {
            engine = renjinEnginePool.acquire();
            return executeWilcoxTest(engine, experimentGroup, controlGroup);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thread interrupted while acquiring Renjin engine", e);
            throw new RuntimeException("Failed to acquire Renjin engine", e);
            
        } catch (Exception e) {
            log.error("Error during Wilcoxon test execution", e);
            throw new RuntimeException("Wilcoxon test failed", e);
            
        } finally {
            if (engine != null) {
                renjinEnginePool.release(engine);
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
    
    public RenjinEnginePool.PoolStatus getPoolStatus() {
        return renjinEnginePool.getStatus();
    }
}
