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

@Slf4j
public class RenjinScriptEnginePoolFactory extends BasePooledObjectFactory<RenjinScriptEngine> {
    
    private final RenjinScriptEngineFactory engineFactory;
    
    public RenjinScriptEnginePoolFactory() {
        this.engineFactory = new RenjinScriptEngineFactory();
    }
    
    @Override
    public RenjinScriptEngine create() throws Exception {
        log.debug("Creating new RenjinScriptEngine instance");
        return engineFactory.getScriptEngine();
    }
    
    @Override
    public PooledObject<RenjinScriptEngine> wrap(RenjinScriptEngine engine) {
        return new DefaultPooledObject<>(engine);
    }
    
    @Override
    public boolean validateObject(PooledObject<RenjinScriptEngine> pooledObject) {
        try {
            RenjinScriptEngine engine = pooledObject.getObject();
            Object result = engine.eval("1 + 1");
            return result != null && ((Number) result).intValue() == 2;
        } catch (ScriptException e) {
            log.error("RenjinScriptEngine validation failed", e);
            return false;
        }
    }
    
    @Override
    public void destroyObject(PooledObject<RenjinScriptEngine> pooledObject) throws Exception {
        log.debug("Destroying RenjinScriptEngine");
    }
}
```

//////////////////////////////////////////////////////////////////////

```java
package com.example.batch.config.pool;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.renjin.script.RenjinScriptEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
public class RenjinEnginePoolConfig {
    
    @Value("${renjin.pool.max-total:30}")
    private int maxTotal;
    
    @Value("${renjin.pool.min-idle:10}")
    private int minIdle;
    
    @Value("${renjin.pool.max-wait-millis:30000}")
    private long maxWaitMillis;
    
    @Bean
    public GenericObjectPoolConfig<RenjinScriptEngine> renjinPoolConfig() {
        GenericObjectPoolConfig<RenjinScriptEngine> config = new GenericObjectPoolConfig<>();
        
        // 필수 설정
        config.setMaxTotal(maxTotal);
        config.setMaxIdle(maxTotal);
        config.setMinIdle(minIdle);
        config.setMaxWait(Duration.ofMillis(maxWaitMillis));
        
        // 권장 기본값
        config.setTestWhileIdle(true);
        config.setTimeBetweenEvictionRuns(Duration.ofMinutes(1));
        config.setMinEvictableIdleTime(Duration.ofMinutes(5));
        config.setBlockWhenExhausted(true);
        config.setLifo(true);
        config.setJmxEnabled(false);
        
        log.info("Renjin Pool Config - MaxTotal: {}, MinIdle: {}, MaxWait: {}ms",
                 maxTotal, minIdle, maxWaitMillis);
        
        return config;
    }
    
    @Bean(destroyMethod = "close")
    public GenericObjectPool<RenjinScriptEngine> renjinEnginePool(
            GenericObjectPoolConfig<RenjinScriptEngine> poolConfig) {
        
        log.info("Initializing Renjin Engine Pool");
        long startTime = System.currentTimeMillis();
        
        RenjinScriptEnginePoolFactory factory = new RenjinScriptEnginePoolFactory();
        GenericObjectPool<RenjinScriptEngine> pool = new GenericObjectPool<>(factory, poolConfig);
        
        try {
            pool.preparePool();
            log.info("Renjin Engine Pool pre-warmed with {} instances", minIdle);
        } catch (Exception e) {
            log.warn("Failed to pre-warm Renjin Engine Pool", e);
        }
        
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

//////////////////////////////////////////////////////////////////////

```java
package com.example.batch.processor;

import com.example.batch.domain.Defect;
import com.example.batch.domain.EcoResult;
import com.example.batch.repository.LotRepository;
import com.example.batch.service.EcoAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EcoAnalysisProcessor implements ItemProcessor<Defect, EcoResult> {
    
    private final EcoAnalysisService ecoAnalysisService;
    private final LotRepository lotRepository;
    
    @Override
    public EcoResult process(Defect defect) throws Exception {
        long startTime = System.currentTimeMillis();
        
        try {
            // Lot 데이터 조회
            double[] experimentGroup = lotRepository.findExperimentData(defect.getId());
            double[] controlGroup = lotRepository.findControlData(defect.getId());
            
            // Wilcoxon test 수행
            Double pValue = ecoAnalysisService.performWilcoxonTest(experimentGroup, controlGroup);
            
            // 결과 생성
            EcoResult result = EcoResult.builder()
                    .defectId(defect.getId())
                    .pValue(pValue)
                    .experimentSize(experimentGroup.length)
                    .controlSize(controlGroup.length)
                    .build();
            
            long elapsed = System.currentTimeMillis() - startTime;
            log.debug("Defect {} processed in {}ms, p-value: {}", 
                     defect.getId(), elapsed, pValue);
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to process defect: {}", defect.getId(), e);
            throw e;
        }
    }
}
