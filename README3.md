# Renjin 엔진 병렬 처리 기능 추가

기존 코드에 **멀티스레드 병렬 처리**, **배치 처리**, **비동기 작업**, **작업 큐** 등의 기능을 추가하겠습니다.

## 1. 병렬 처리 서비스 (RenjinParallelService)

```java
package com.example.batch.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.renjin.script.RenjinScriptEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.example.batch.config.pool.RenjinEnginePoolManager;

import javax.script.ScriptException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RenjinParallelService {
    
    private final RenjinEnginePoolManager poolManager;
    
    @Value("${renjin.parallel.thread-pool-size:0}")
    private int threadPoolSize;
    
    private ExecutorService executorService;
    
    @PostConstruct
    public void init() {
        // CPU 코어 수만큼 스레드 풀 생성 (0이면 자동 설정)
        int poolSize = threadPoolSize <= 0 
            ? Runtime.getRuntime().availableProcessors() 
            : threadPoolSize;
        
        this.executorService = Executors.newFixedThreadPool(
            poolSize,
            new ThreadFactory() {
                private final AtomicInteger count = new AtomicInteger(0);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r);
                    t.setName("RenjinParallel-" + count.incrementAndGet());
                    t.setDaemon(false);
                    return t;
                }
            }
        );
        
        log.info("Renjin Parallel Service initialized with {} threads", poolSize);
    }
    
    /**
     * 여러 R 스크립트를 병렬로 실행합니다.
     */
    public List<Object> executeScriptsParallel(List<String> scripts) throws Exception {
        log.info("Executing {} scripts in parallel", scripts.size());
        
        List<Future<Object>> futures = new ArrayList<>();
        
        for (String script : scripts) {
            Future<Object> future = executorService.submit(() -> {
                RenjinScriptEngine engine = null;
                try {
                    engine = poolManager.borrowEngine();
                    long startTime = System.currentTimeMillis();
                    
                    Object result = engine.eval(script);
                    
                    long elapsed = System.currentTimeMillis() - startTime;
                    log.debug("Script executed in {}ms", elapsed);
                    
                    return result;
                    
                } catch (Exception e) {
                    log.error("Error executing script", e);
                    if (engine != null) {
                        poolManager.invalidateEngine(engine);
                    }
                    throw new RuntimeException("Script execution failed", e);
                    
                } finally {
                    if (engine != null) {
                        poolManager.returnEngine(engine);
                    }
                }
            });
            
            futures.add(future);
        }
        
        // 모든 결과 수집
        return collectResults(futures);
    }
    
    /**
     * 데이터를 청크로 분할하여 병렬 처리합니다.
     */
    public <T, R> List<R> processChunksParallel(List<T> data, int chunkSize, 
                                                ChunkProcessor<T, R> processor) throws Exception {
        log.info("Processing {} items in chunks of {}", data.size(), chunkSize);
        
        List<List<T>> chunks = partitionList(data, chunkSize);
        List<Future<R>> futures = new ArrayList<>();
        
        for (List<T> chunk : chunks) {
            Future<R> future = executorService.submit(() -> {
                RenjinScriptEngine engine = null;
                try {
                    engine = poolManager.borrowEngine();
                    return processor.process(engine, chunk);
                    
                } catch (Exception e) {
                    log.error("Error processing chunk", e);
                    if (engine != null) {
                        poolManager.invalidateEngine(engine);
                    }
                    throw new RuntimeException("Chunk processing failed", e);
                    
                } finally {
                    if (engine != null) {
                        poolManager.returnEngine(engine);
                    }
                }
            });
            
            futures.add(future);
        }
        
        return collectResults(futures);
    }
    
    /**
     * 맵-리듀스 방식으로 데이터를 처리합니다.
     */
    public <T, K, V> Map<K, V> mapReduceParallel(List<T> data, int chunkSize,
                                                  MapFunction<T, K, V> mapper,
                                                  ReduceFunction<K, V> reducer) throws Exception {
        log.info("Map-Reduce processing for {} items", data.size());
        
        // 1. Map Phase
        List<List<T>> chunks = partitionList(data, chunkSize);
        List<Future<Map<K, V>>> mapFutures = new ArrayList<>();
        
        for (List<T> chunk : chunks) {
            Future<Map<K, V>> future = executorService.submit(() -> {
                RenjinScriptEngine engine = null;
                try {
                    engine = poolManager.borrowEngine();
                    
                    Map<K, V> result = new HashMap<>();
                    for (T item : chunk) {
                        K key = mapper.getKey(item);
                        V value = mapper.map(engine, item);
                        result.put(key, value);
                    }
                    return result;
                    
                } catch (Exception e) {
                    log.error("Error in map phase", e);
                    if (engine != null) {
                        poolManager.invalidateEngine(engine);
                    }
                    throw new RuntimeException("Map phase failed", e);
                    
                } finally {
                    if (engine != null) {
                        poolManager.returnEngine(engine);
                    }
                }
            });
            
            mapFutures.add(future);
        }
        
        // 2. Reduce Phase
        Map<K, V> finalResult = new HashMap<>();
        for (Future<Map<K, V>> future : mapFutures) {
            try {
                Map<K, V> partialResult = future.get(5, TimeUnit.MINUTES);
                for (Map.Entry<K, V> entry : partialResult.entrySet()) {
                    finalResult.put(entry.getKey(), 
                        reducer.reduce(entry.getValue(), 
                                     finalResult.getOrDefault(entry.getKey(), null)));
                }
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new RuntimeException("Map-Reduce timeout", e);
            }
        }
        
        return finalResult;
    }
    
    /**
     * 배치로 데이터를 처리합니다 (콜백 방식).
     */
    public <T> void processBatchParallel(List<T> data, int batchSize,
                                         BatchProcessor<T> processor) throws Exception {
        log.info("Batch processing {} items with batch size {}", data.size(), batchSize);
        
        List<List<T>> batches = partitionList(data, batchSize);
        List<Future<Void>> futures = new ArrayList<>();
        
        for (int i = 0; i < batches.size(); i++) {
            final int batchIndex = i;
            List<T> batch = batches.get(i);
            
            Future<Void> future = executorService.submit(() -> {
                RenjinScriptEngine engine = null;
                try {
                    engine = poolManager.borrowEngine();
                    processor.processBatch(engine, batch, batchIndex);
                    return null;
                    
                } catch (Exception e) {
                    log.error("Error processing batch {}", batchIndex, e);
                    if (engine != null) {
                        poolManager.invalidateEngine(engine);
                    }
                    throw new RuntimeException("Batch processing failed", e);
                    
                } finally {
                    if (engine != null) {
                        poolManager.returnEngine(engine);
                    }
                }
            });
            
            futures.add(future);
        }
        
        // 모든 배치 완료 대기
        for (Future<Void> future : futures) {
            try {
                future.get(10, TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new RuntimeException("Batch processing timeout", e);
            }
        }
        
        log.info("Batch processing completed");
    }
    
    /**
     * 스트림 방식으로 데이터를 처리합니다 (큐 기반).
     */
    public <T, R> BlockingQueue<R> processStreamParallel(Iterator<T> dataIterator,
                                                         int queueSize,
                                                         StreamProcessor<T, R> processor) {
        BlockingQueue<R> resultQueue = new LinkedBlockingQueue<>(queueSize);
        
        // 생산자 스레드
        executorService.submit(() -> {
            RenjinScriptEngine engine = null;
            try {
                engine = poolManager.borrowEngine();
                
                while (dataIterator.hasNext()) {
                    T item = dataIterator.next();
                    try {
                        R result = processor.process(engine, item);
                        resultQueue.put(result);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
                // 종료 신호
                resultQueue.put((R) "END_OF_STREAM");
                
            } catch (Exception e) {
                log.error("Error in stream producer", e);
                if (engine != null) {
                    poolManager.invalidateEngine(engine);
                }
            } finally {
                if (engine != null) {
                    poolManager.returnEngine(engine);
                }
            }
        });
        
        return resultQueue;
    }
    
    /**
     * 비동기 작업을 실행합니다.
     */
    public CompletableFuture<Object> executeAsyncScript(String script) {
        return CompletableFuture.supplyAsync(() -> {
            RenjinScriptEngine engine = null;
            try {
                engine = poolManager.borrowEngine();
                return engine.eval(script);
                
            } catch (Exception e) {
                log.error("Error executing async script", e);
                if (engine != null) {
                    poolManager.invalidateEngine(engine);
                }
                throw new RuntimeException("Async script execution failed", e);
                
            } finally {
                if (engine != null) {
                    poolManager.returnEngine(engine);
                }
            }
        }, executorService);
    }
    
    /**
     * 여러 비동기 작업을 결합합니다.
     */
    public CompletableFuture<List<Object>> combineAsyncScripts(List<String> scripts) {
        List<CompletableFuture<Object>> futures = scripts.stream()
            .map(this::executeAsyncScript)
            .collect(Collectors.toList());
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()));
    }
    
    /**
     * 조건부 병렬 실행 (조건을 만족하는 항목만 처리).
     */
    public <T> List<Object> executeConditionalParallel(List<T> data,
                                                       Predicate<T> condition,
                                                       ConditionalProcessor<T> processor) throws Exception {
        log.info("Conditional parallel processing for {} items", data.size());
        
        List<Future<Object>> futures = new ArrayList<>();
        
        for (T item : data) {
            if (condition.test(item)) {
                Future<Object> future = executorService.submit(() -> {
                    RenjinScriptEngine engine = null;
                    try {
                        engine = poolManager.borrowEngine();
                        return processor.process(engine, item);
                        
                    } catch (Exception e) {
                        log.error("Error in conditional processing", e);
                        if (engine != null) {
                            poolManager.invalidateEngine(engine);
                        }
                        throw new RuntimeException("Conditional processing failed", e);
                        
                    } finally {
                        if (engine != null) {
                            poolManager.returnEngine(engine);
                        }
                    }
                });
                
                futures.add(future);
            }
        }
        
        return collectResults(futures);
    }
    
    /**
     * 리스트를 청크로 분할합니다.
     */
    private <T> List<List<T>> partitionList(List<T> list, int partitionSize) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += partitionSize) {
            partitions.add(list.subList(i, Math.min(i + partitionSize, list.size())));
        }
        return partitions;
    }
    
    /**
     * Future 결과들을 수집합니다.
     */
    private <T> List<T> collectResults(List<Future<T>> futures) throws Exception {
        List<T> results = new ArrayList<>();
        
        for (Future<T> future : futures) {
            try {
                results.add(future.get(5, TimeUnit.MINUTES));
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new RuntimeException("Result collection timeout", e);
            }
        }
        
        return results;
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down Renjin Parallel Service");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    // Functional Interfaces
    @FunctionalInterface
    public interface ChunkProcessor<T, R> {
        R process(RenjinScriptEngine engine, List<T> chunk) throws Exception;
    }
    
    @FunctionalInterface
    public interface MapFunction<T, K, V> {
        K getKey(T item);
        V map(RenjinScriptEngine engine, T item) throws Exception;
    }
    
    @FunctionalInterface
    public interface ReduceFunction<K, V> {
        V reduce(V value1, V value2);
    }
    
    @FunctionalInterface
    public interface BatchProcessor<T> {
        void processBatch(RenjinScriptEngine engine, List<T> batch, int batchIndex) throws Exception;
    }
    
    @FunctionalInterface
    public interface StreamProcessor<T, R> {
        R process(RenjinScriptEngine engine, T item) throws Exception;
    }
    
    @FunctionalInterface
    public interface ConditionalProcessor<T> {
        Object process(RenjinScriptEngine engine, T item) throws Exception;
    }
}
```

## 2. 업데이트된 EcoAnalysisService (병렬 처리 추가)

```java
package com.example.batch.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.renjin.script.RenjinScriptEngine;
import org.springframework.stereotype.Service;
import com.example.batch.config.pool.RenjinEnginePoolManager;

import javax.script.ScriptException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EcoAnalysisService {
    
    private final RenjinEnginePoolManager poolManager;
    private final RenjinParallelService parallelService;
    
    /**
     * 단일 Wilcoxon 테스트
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
            }
            throw new RuntimeException("Wilcoxon test failed", e);
            
        } finally {
            if (engine != null) {
                poolManager.returnEngine(engine);
            }
        }
    }
    
    /**
     * 여러 Wilcoxon 테스트를 병렬로 실행
     */
    public List<WilcoxTestResult> performWilcoxonTestsParallel(
            List<WilcoxTestData> testDataList) throws Exception {
        
        log.info("Performing {} Wilcoxon tests in parallel", testDataList.size());
        
        List<String> scripts = testDataList.stream()
            .map(this::buildWilcoxScript)
            .collect(Collectors.toList());
        
        List<Object> results = parallelService.executeScriptsParallel(scripts);
        
        List<WilcoxTestResult> testResults = new ArrayList<>();
        for (int i = 0; i < testDataList.size(); i++) {
            testResults.add(new WilcoxTestResult(
                testDataList.get(i).getName(),
                ((Number) results.get(i)).doubleValue()
            ));
        }
        
        return testResults;
    }
    
    /**
     * 배치로 통계 분석을 수행
     */
    public void analyzeDatasetsBatch(List<DatasetInfo> datasets, int batchSize) throws Exception {
        log.info("Batch analyzing {} datasets", datasets.size());
        
        parallelService.processBatchParallel(datasets, batchSize,
            (engine, batch, batchIndex) -> {
                for (DatasetInfo dataset : batch) {
                    try {
                        String script = String.format(
                            "summary(c(%s))",
                            String.join(",", dataset.getValues())
                        );
                        Object result = engine.eval(script);
                        dataset.setSummary(result.toString());
                        
                        log.debug("Batch {}: Analyzed dataset {}", 
                                 batchIndex, dataset.getName());
                    } catch (ScriptException e) {
                        log.error("Error analyzing dataset {}", dataset.getName(), e);
                        throw e;
                    }
                }
            });
    }
    
    /**
     * 청크 단위로 데이터 처리
     */
    public List<StatisticalResult> analyzeChunkedData(List<Double> data, int chunkSize) throws Exception {
        log.info("Analyzing {} data points in chunks of {}", data.size(), chunkSize);
        
        return parallelService.processChunksParallel(data, chunkSize,
            (engine, chunk) -> {
                RenjinScriptEngine scriptEngine = engine;
                double[] values = chunk.stream()
                    .mapToDouble(Double::doubleValue)
                    .toArray();
                
                scriptEngine.put("values", values);
                
                try {
                    String script = "list(mean=mean(values), sd=sd(values), " +
                                   "min=min(values), max=max(values), " +
                                   "median=median(values))";
                    Object result = scriptEngine.eval(script);
                    
                    return new StatisticalResult(
                        chunk.size(),
                        result.toString()
                    );
                    
                } catch (ScriptException e) {
                    log.error("Error processing chunk", e);
                    throw e;
                }
            });
    }
    
    /**
     * 조건부 병렬 분석
     */
    public List<Object> analyzeConditional(List<EcosystemData> ecosystemData) throws Exception {
        log.info("Conditional analysis for {} ecosystems", ecosystemData.size());
        
        return parallelService.executeConditionalParallel(
            ecosystemData,
            // 조건: 건강도가 낮은 생태계만 분석
            data -> data.getHealthScore() < 50,
            
            (engine, data) -> {
                engine.put("biodiversity", data.getBiodiversityIndex());
                engine.put("pollution", data.getPollutionLevel());
                
                String script = "prediction <- biodiversity - (pollution * 0.5); " +
                               "if(prediction < 30) 'CRITICAL' else 'ALERT'";
                
                return engine.eval(script);
            }
        );
    }
    
    /**
     * Map-Reduce로 빅데이터 분석
     */
    public Map<String, Double> analyzeMapReduce(List<SpeciesData> speciesList) throws Exception {
        log.info("Map-Reduce analysis for {} species", speciesList.size());
        
        return parallelService.mapReduceParallel(
            speciesList,
            100, // 청크 크기
            
            // Map: 종별 개수 집계
            new RenjinParallelService.MapFunction<SpeciesData, String, Double>() {
                @Override
                public String getKey(SpeciesData item) {
                    return item.getCategory();
                }
                
                @Override
                public Double map(RenjinScriptEngine engine, SpeciesData item) throws Exception {
                    return (double) item.getCount();
                }
            },
            
            // Reduce: 합산
            (value1, value2) -> {
                if (value2 == null) return value1;
                return value1 + value2;
            }
        );
    }
    
    /**
     * 비동기로 복잡한 분석 수행
     */
    public java.util.concurrent.CompletableFuture<ComplexAnalysisResult> analyzeComplexAsync(
            List<String> analysisScripts) {
        
        return parallelService.combineAsyncScripts(analysisScripts)
            .thenApply(results -> {
                ComplexAnalysisResult analysisResult = new ComplexAnalysisResult();
                analysisResult.setResults(results);
                analysisResult.setCompletionTime(System.currentTimeMillis());
                return analysisResult;
            });
    }
    
    // Helper Methods
    
    private String buildWilcoxScript(WilcoxTestData data) {
        return String.format(
            "exp <- c(%s); ctrl <- c(%s); " +
            "result <- wilcox.test(exp, ctrl, exact=FALSE); result$p.value",
            String.join(",", data.getExperiment()),
            String.join(",", data.getControl())
        );
    }
    
    private Double executeWilcoxTest(RenjinScriptEngine engine,
                                     double[] experimentGroup,
                                     double[] controlGroup) throws ScriptException {
        engine.put("experiment", experimentGroup);
        engine.put("control", controlGroup);
        
        String script = "result <- wilcox.test(experiment, control, exact=FALSE); result$p.value";
        Object result = engine.eval(script);
        
        return result != null ? ((Number) result).doubleValue() : null;
    }
    
    // Pool 상태 조회
    
    public RenjinEnginePoolManager.PoolStatus getPoolStatus() {
        return poolManager.getStatus();
    }
    
    public RenjinEnginePoolManager.PoolStatistics getPoolStatistics() {
        return poolManager.getStatistics();
    }
    
    // DTOs
    
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class WilcoxTestData {
        private String name;
        private List<String> experiment;
        private List<String> control;
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class WilcoxTestResult {
        private String testName;
        private Double pValue;
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class DatasetInfo {
        private String name;
        private List<String> values;
        private String summary;
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class StatisticalResult {
        private int sampleSize;
        private String statistics;
    }
    
    @lombok.Data
    public static class EcosystemData {
        private String name;
        private double healthScore;
        private double biodiversityIndex;
        private double pollutionLevel;
    }
    
    @lombok.Data
    public static class SpeciesData {
        private String name;
        private String category;
        private int count;
    }
    
    @lombok.Data
    public static class ComplexAnalysisResult {
        private List<Object> results;
        private long completionTime;
    }
}
```

## 3. application.yml 추가 설정

```yaml
renjin:
  pool:
    max-total: 30
    max-idle: 30
    min-idle: 10
    max-wait-millis: 30000
    test-while-idle: true
    time-between-eviction-runs-millis: 60000
    min-evictable-idle-time-millis: 300000
  
  # 병렬 처리 설정
  parallel:
    thread-pool-size: 0  # 0 = CPU 코어 수
    queue-size: 1000
    timeout-minutes: 10
```

## 4. 컨트롤러 예제

```java
package com.example.batch.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import com.example.batch.service.EcoAnalysisService;
import com.example.batch.config.pool.RenjinEnginePoolManager;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/eco-analysis")
@RequiredArgsConstructor
public class EcoAnalysisController {
    
    private final EcoAnalysisService ecoAnalysisService;
    
    /**
     * 여러 Wilcoxon 테스트 병렬 실행
     */
    @PostMapping("/wilcoxon-parallel")
    public List<EcoAnalysisService.WilcoxTestResult> wilcoxonTestsParallel(
            @RequestBody List<EcoAnalysisService.WilcoxTestData> testDataList) throws Exception {
        return ecoAnalysisService.performWilcoxonTestsParallel(testDataList);
    }
    
    /**
     * 배치 데이터 분석
     */
    @PostMapping("/analyze-batch")
    public void analyzeBatch(
            @RequestBody List<EcoAnalysisService.DatasetInfo> datasets,
            @RequestParam(defaultValue = "10") int batchSize) throws Exception {
        ecoAnalysisService.analyzeDatasetsBatch(datasets, batchSize);
    }
    
    /**
     * 청크 단위 분석
     */
    @PostMapping("/analyze-chunks")
    public List<EcoAnalysisService.StatisticalResult> analyzeChunks(
            @RequestBody List<Double> data,
            @RequestParam(defaultValue = "100") int chunkSize) throws Exception {
        return ecoAnalysisService.analyzeChunkedData(data, chunkSize);
    }
    
    /**
     * Map-Reduce 분석
     */
    @PostMapping("/analyze-mapreduce")
    public Map<String, Double> analyzeMapReduce(
            @RequestBody List<EcoAnalysisService.SpeciesData> speciesList) throws Exception {
        return ecoAnalysisService.analyzeMapReduce(speciesList);
    }
    
    /**
     * 풀 상태 조회
     */
    @GetMapping("/pool-status")
    public RenjinEnginePoolManager.PoolStatus getPoolStatus() {
        return ecoAnalysisService.getPoolStatus();
    }
    
    /**
     * 풀 통계 조회
     */
    @GetMapping("/pool-statistics")
    public RenjinEnginePoolManager.PoolStatistics getPoolStatistics() {
        return ecoAnalysisService.getPoolStatistics();
    }
}
```
