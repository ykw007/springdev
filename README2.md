## Docker 컨테이너의 Spring Batch + Renjin + Xeon CPU 성능 최적화

Docker 환경에서 Spring Batch의 멀티스레드 처리 중 각 스레드가 Renjin 엔진으로 R 스크립트를 실행할 때 Xeon CPU의 성능을 제대로 활용하려면 **JVM 레벨, Docker 레벨, 그리고 Renjin 레벨**에서 동시에 최적화가 필요합니다.

### 1. **JVM (Java) 레벨 최적화**

#### 핵심 JVM 옵션

```bash
# Docker 실행 시 환경 변수 또는 java -D 옵션으로 설정
JAVA_OPTS="-XX:ActiveProcessorCount=<할당된_코어_수> \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+ParallelRefProcEnabled \
  -Djava.security.egd=file:/dev/./urandom \
  -XX:+UseStringDeduplication"
```

**설명:**
- **`-XX:ActiveProcessorCount=<N>`**: JVM이 인식하는 코어 수를 명시적으로 설정 (Xeon 48코어 전체가 아닌 실제 Docker에 할당된 코어 수로 제한)
- **`-XX:+UseG1GC`**: G1 가비지 컬렉터로 대규모 힙 메모리 관리 최적화
- **`-XX:MaxGCPauseMillis=200`**: GC 일시 정지 시간 제한으로 응답성 향상
- **`-XX:+ParallelRefProcEnabled`**: 병렬 참조 처리로 멀티스레드 환경 최적화
- **`-Djava.security.egd=file:/dev/./urandom`**: 난수 생성 블로킹 제거 (Renjin이 보안 토큰 생성 시 중요)
- **`-XX:+UseStringDeduplication`**: 문자열 메모리 최적화 (R 스크립트 처리 시 유용)

#### Renjin 특화 옵션

```bash
JAVA_OPTS="... \
  -Drenjin.compiler.type=JIT \
  -Drenjin.enable.lazy.evaluation=true \
  -Drenjin.memory.limit=<메모리MB>"
```

**설명:**
- **`-Drenjin.compiler.type=JIT`**: Renjin의 JIT 컴파일러 활성화로 R 코드 성능 향상
- **`-Drenjin.enable.lazy.evaluation=true`**: 지연 평가(Lazy Evaluation)로 불필요한 연산 제거
- **`-Drenjin.memory.limit`**: 각 Renjin 엔진의 메모리 제한 설정 (멀티스레드 환경에서 메모리 폭증 방지)

---

### 2. **Docker 레벨 최적화**

#### Dockerfile 예시

```dockerfile
FROM openjdk:11-jre-slim

# Xeon CPU 친화성 최적화
ENV JAVA_OPTS="-XX:ActiveProcessorCount=12 \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+ParallelRefProcEnabled \
  -Djava.security.egd=file:/dev/./urandom \
  -Drenjin.compiler.type=JIT"

ENV JVM_MEMORY="-Xms4g -Xmx8g"

COPY app.jar application.jar

ENTRYPOINT ["sh", "-c", "java ${JVM_MEMORY} ${JAVA_OPTS} -jar application.jar"]
```

#### docker-compose.yml 또는 Docker run 명령어

```yaml
version: '3.8'
services:
  spring-batch-app:
    image: spring-batch-renjin:latest
    container_name: batch_processor
    
    # CPU 할당 (Xeon 48코어 중 일부만 사용)
    cpus: '4.0'  # 4코어 할당
    cpuset: '0-3'  # 특정 코어 고정 (CPU affinity)
    
    # 메모리 할당
    mem_limit: 16g
    memswap_limit: 16g
    
    # CPU 스케줄링 우선순위
    cpu_shares: 1024
    
    # 추가 환경 변수
    environment:
      JAVA_OPTS: "-XX:ActiveProcessorCount=4 -XX:+UseG1GC -Drenjin.compiler.type=JIT"
      JVM_MEMORY: "-Xms4g -Xmx8g"
    
    # 호스트 네트워크 모드 (성능 향상)
    network_mode: host
```

**주요 설정:**
- **`cpus: '4.0'`**: 컨테이너에 4코어 할당 (실제 필요에 맞게 조정)
- **`cpuset: '0-3'`**: CPU affinity를 통해 특정 물리 코어에 고정 (컨텍스트 스위칭 감소)
- **`cpu_shares: 1024`**: CPU 스케줄링 가중치
- **`network_mode: host`**: 호스트 네트워크 모드로 네트워크 오버헤드 제거

#### 실행 명령어

```bash
docker run -it \
  --cpus="4.0" \
  --cpuset-cpus="0-3" \
  --memory="16g" \
  -e JAVA_OPTS="-XX:ActiveProcessorCount=4 -XX:+UseG1GC -Drenjin.compiler.type=JIT" \
  -e JVM_MEMORY="-Xms4g -Xmx8g" \
  --name batch_processor \
  spring-batch-renjin:latest
```

---

### 3. **Spring Batch 설정 최적화**

#### TaskExecutor 및 스레드 풀 설정

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class BatchConfiguration {

    @Bean(name = "batchTaskExecutor")
    public TaskExecutor batchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 코어 스레드 수: 할당된 CPU 코어 수와 동일
        executor.setCorePoolSize(4);  // Docker에서 할당한 코어 수와 일치
        
        // 최대 스레드 수: 코어 수의 2배 (IO 대기 시간 고려)
        executor.setMaxPoolSize(8);
        
        // 큐 크기: 대기 작업의 최대 개수
        executor.setQueueCapacity(256);
        
        // 스레드 이름 (디버깅용)
        executor.setThreadNamePrefix("batch-renjin-");
        
        // 거부 정책 (큐가 가득 찼을 때)
        executor.setRejectedExecutionHandler(
            new ThreadPoolTaskExecutor.CallerRunsPolicy()
        );
        
        executor.initialize();
        return executor;
    }
}
```

#### Spring Batch Step 설정 (멀티스레드)

```java
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class RenjinBatchStepFactory {

    @Autowired
    private StepBuilderFactory stepBuilderFactory;

    @Autowired
    private TaskExecutor batchTaskExecutor;

    public Step renjinProcessingStep(
            ItemReader<InputData> reader,
            ItemProcessor<InputData, OutputData> processor,
            ItemWriter<OutputData> writer) {
        
        return stepBuilderFactory.get("renjinProcessingStep")
                .<InputData, OutputData>chunk(100)  // 청크 크기 조정 (메모리 고려)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .taskExecutor(batchTaskExecutor)  // 멀티스레드 실행
                .throttleLimit(8)  // 동시 실행 청크 수 (MaxPoolSize와 동일)
                .build();
    }
}
```

---

### 4. **Renjin 엔진 레벨 최적화**

#### Renjin 멀티스레드 처리

```java
import org.renjin.script.RenjinScriptEngineFactory;
import org.renjin.sexp.SEXP;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RenjinThreadSafeProcessor {

    // 스레드별 Renjin 엔진 인스턴스 (각 스레드가 자신의 엔진을 가짐)
    private final ThreadLocal<ScriptEngine> threadLocalEngine = 
        ThreadLocal.withInitial(this::createRenjinEngine);

    private ScriptEngine createRenjinEngine() {
        return new RenjinScriptEngineFactory().getScriptEngine();
    }

    @Bean
    public ItemProcessor<InputData, OutputData> renjinProcessor() {
        return item -> {
            try {
                ScriptEngine engine = threadLocalEngine.get();
                
                // 입력 데이터를 R 변수로 설정
                engine.put("input_data", item.getValue());
                
                // R 스크립트 실행 (컴파일된 스크립트 캐싱)
                String rScript = loadRScript("analysis.R");
                SEXP result = (SEXP) engine.eval(rScript);
                
                // 결과 반환
                OutputData output = new OutputData();
                output.setResult(result.asDouble());
                return output;
                
            } catch (ScriptException e) {
                throw new RuntimeException("Renjin 스크립트 실행 오류", e);
            }
        };
    }

    // 스레드 종료 시 리소스 정리
    public void cleanupThreadLocal() {
        threadLocalEngine.remove();
    }
}
```

#### Renjin 스크립트 캐싱 (성능 향상)

```java
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RenjinScriptCache {

    private final Map<String, CompiledScript> scriptCache = 
        new ConcurrentHashMap<>();

    public CompiledScript getCompiledScript(String scriptPath) 
            throws ScriptException {
        return scriptCache.computeIfAbsent(scriptPath, path -> {
            try {
                String script = loadScriptContent(path);
                Compilable compilable = (Compilable) 
                    new RenjinScriptEngineFactory().getScriptEngine();
                return compilable.compile(script);
            } catch (ScriptException e) {
                throw new RuntimeException("스크립트 컴파일 실패", e);
            }
        });
    }

    private String loadScriptContent(String path) {
        // 파일에서 R 스크립트 로드
        return Files.readString(Paths.get(path));
    }
}
```

---

### 5. **성능 모니터링 및 검증**

#### 실행 중 성능 확인

```bash
# 컨테이너의 CPU 사용량 및 Throttling 확인
docker stats --no-stream batch_processor

# Xeon CPU 코어별 사용률 확인
top -p $(pgrep -f "java.*jar")

# JVM 힙 메모리 및 스레드 상태 확인
jcmd <PID> Thread.print | head -50
jcmd <PID> GC.heap_dump filename=heap.bin
```

#### 애플리케이션 내 성능 로깅

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class PerformanceMonitor {

    private static final Logger logger = 
        LoggerFactory.getLogger(PerformanceMonitor.class);

    public void monitorBatchExecution(String taskName, Runnable task) {
        long startTime = System.nanoTime();
        long threadId = Thread.currentThread().getId();
        
        try {
            task.run();
        } finally {
            long elapsedTime = (System.nanoTime() - startTime) / 1_000_000;
            logger.info("Task: {}, Thread: {}, Duration: {}ms",
                taskName, threadId, elapsedTime);
        }
    }
}
```

---

### 6. **최적화된 실행 예시 (완전한 설정)**

```bash
#!/bin/bash

docker run -d \
  --name spring-batch-renjin \
  --cpus="4.0" \
  --cpuset-cpus="0-3" \
  --memory="16g" \
  --memswap-limit="16g" \
  --cpu-shares=1024 \
  -e JAVA_OPTS="-XX:ActiveProcessorCount=4 \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+ParallelRefProcEnabled \
    -Djava.security.egd=file:/dev/./urandom \
    -Drenjin.compiler.type=JIT \
    -Drenjin.enable.lazy.evaluation=true" \
  -e JVM_MEMORY="-Xms4g -Xmx8g" \
  -e SPRING_BATCH_THREAD_POOL_SIZE=4 \
  -e SPRING_BATCH_MAX_POOL_SIZE=8 \
  spring-batch-renjin:latest
```

---

### 요약 체크리스트

| 항목 | 설정 | 효과 |
|------|------|------|
| **JVM 코어 수** | `-XX:ActiveProcessorCount=4` | 과도한 스레드 생성 방지 |
| **GC 최적화** | `-XX:+UseG1GC` | 대규모 메모리 관리 |
| **난수 생성** | `-Djava.security.egd=file:/dev/./urandom` | Renjin 엔진 블로킹 제거 |
| **Renjin JIT** | `-Drenjin.compiler.type=JIT` | R 스크립트 실행 속도 향상 |
| **Docker CPU할당** | `--cpus="4.0" --cpuset-cpus="0-3"` | CPU Affinity로 Xeon 성능 활용 |
| **스레드 풀** | `CorePoolSize=4, MaxPoolSize=8` | 효율적인 멀티스레딩 |
| **Renjin 엔진 인스턴스** | `ThreadLocal<ScriptEngine>` | 스레드 안전성 + 성능 |
| **메모리 제한** | `--memory="16g"` | OOM 방지 및 GC 효율성 |




## 대용량 처리 Spring Batch 시스템에서 Renjin 엔진을 사용하는 Docker 컨테이너 설정 사례

다음은 Spring Batch와 Renjin 엔진을 사용하는 Docker 컨테이너 설정의 예시입니다. 이 설정은 대용량 데이터를 처리할 수 있도록 최적화되어 있습니다.

### 1. **Dockerfile 설정**

Dockerfile은 애플리케이션을 빌드하고 최적화된 실행 환경을 설정하는 데 사용됩니다.

```dockerfile
# 베이스 이미지 설정
FROM openjdk:11-jre-slim

# JVM 및 Renjin 설정
ENV JAVA_OPTS="-XX:ActiveProcessorCount=4 \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+ParallelRefProcEnabled \
  -Djava.security.egd=file:/dev/./urandom \
  -Drenjin.compiler.type=JIT \
  -Drenjin.enable.lazy.evaluation=true \
  -Drenjin.memory.limit=2048"  # Renjin 메모리 제한

# 메모리 설정
ENV JVM_MEMORY="-Xms4g -Xmx8g"  # 최소 4GB, 최대 8GB 메모리 할당

# 애플리케이션 JAR 복사
COPY app.jar application.jar

# ENTRYPOINT 설정
ENTRYPOINT ["sh", "-c", "java ${JVM_MEMORY} ${JAVA_OPTS} -jar application.jar"]
```

### 2. **docker-compose.yml 설정**

Docker Compose를 사용하여 컨테이너를 쉽게 관리하고 설정할 수 있습니다. 아래는 Spring Batch와 Renjin을 위한 Compose 설정 예시입니다.

```yaml
version: '3.8'

services:
  spring-batch-app:
    build: .
    image: spring-batch-renjin:latest
    container_name: spring_batch_renjin
    cpus: '4.0'  # 컨테이너에 할당할 CPU 수
    mem_limit: 8g  # 컨테이너에 할당할 메모리
    environment:
      JAVA_OPTS: "-XX:ActiveProcessorCount=4 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"
      JVM_MEMORY: "-Xms4g -Xmx8g"
    volumes:
      - ./data:/data  # 데이터 파일을 호스트와 공유
    networks:
      - spring-network

networks:
  spring-network:
    driver: bridge
```

### 3. **Spring Batch 설정**

Spring Batch의 멀티스레드 처리를 위해 `TaskExecutor`를 설정하고 Renjin 엔진을 사용하는 `ItemProcessor`를 정의합니다.

```java
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableBatchProcessing
public class BatchConfig {

    @Bean
    public ItemReader<InputData> reader() {
        // 데이터 읽기 로직 구현
        return new ListItemReader<>(getInputData());
    }

    @Bean
    public ItemProcessor<InputData, OutputData> processor() {
        return new RenjinProcessor();  // Renjin을 사용하는 프로세서
    }

    @Bean
    public ItemWriter<OutputData> writer() {
        // 데이터 쓰기 로직 구현
        return new DatabaseItemWriter();  // DB에 저장하는 예시
    }

    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(256);
        executor.setThreadNamePrefix("batch-renjin-");
        executor.initialize();
        return executor;
    }

    @Bean
    public Step renjinProcessingStep(StepBuilderFactory stepBuilderFactory) {
        return stepBuilderFactory.get("renjinProcessingStep")
                .<InputData, OutputData>chunk(100)
                .reader(reader())
                .processor(processor())
                .writer(writer())
                .taskExecutor(taskExecutor())
                .throttleLimit(8)
                .build();
    }
    
    // 입력 데이터 및 기타 필요한 메서드 구현
}
```

### 4. **Renjin 엔진 설정**

Renjin 엔진을 사용하는 `ItemProcessor`의 예시입니다. R 스크립트를 실행하여 데이터를 처리합니다.

```java
import org.renjin.script.RenjinScriptEngineFactory;
import org.renjin.sexp.SEXP;

import javax.script.ScriptEngine;
import javax.script.ScriptException;

public class RenjinProcessor implements ItemProcessor<InputData, OutputData> {

    private final ScriptEngine engine = new RenjinScriptEngineFactory().getScriptEngine();

    @Override
    public OutputData process(InputData item) {
        try {
            // R 스크립트 실행
            engine.put("input_data", item.getValue());
            String rScript = "result <- input_data + 1";  // 간단한 R 스크립트 예시
            engine.eval(rScript);
            SEXP result = (SEXP) engine.eval("result");
            return new OutputData(result.asDouble());
        } catch (ScriptException e) {
            throw new RuntimeException("Error executing R script", e);
        }
    }
}
```

### 5. **실행 방법**

이제 설정이 완료되었습니다. 다음 명령어로 컨테이너를 빌드하고 실행할 수 있습니다.

```bash
# Docker 이미지 빌드
docker-compose build

# Docker 컨테이너 실행
docker-compose up
```

### 요약

위의 설정을 통해 Spring Batch와 Renjin 엔진을 사용하는 대용량 처리 시스템을 Docker 컨테이너 내에서 효율적으로 운영할 수 있습니다. 이 구성은 성능을 최적화하고, 멀티스레드 처리를 지원하며, R 스크립트를 쉽게 실행할 수 있는 환경을 제공합니다.

