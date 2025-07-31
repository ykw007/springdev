현재 코드의 문제점은 다음과 같습니다:

1. **스레드풀 생명주기 관리 문제**

   * `ExecutorService`가 Spring 컨텍스트와 함께 안전하게 관리되지 않음.
   * `shutdown()`이 명시적으로 호출되지 않으면 배포 시 종료가 지연될 수 있음.

2. **Spring Kafka의 컨테이너 관리 기능과 중복**

   * Spring Kafka가 자체적으로 `concurrency` 옵션을 제공하는데, 추가적으로 별도의 스레드풀을 만들어 사용하면 **불필요한 중첩 멀티스레드** 구조가 됩니다.

3. **고정 스레드풀(Executors.newFixedThreadPool(10)) 문제**

   * 요청이 몰리면 큐에 작업이 무한히 쌓여 메모리 사용량이 증가할 수 있음.

---

## ✅ **수정된 코드 (Spring Bean 기반 ThreadPool 관리 + 안전한 종료)**

Spring이 관리하는 `ThreadPoolTaskExecutor`를 사용하여 스레드풀을 관리하면
서버 종료 시 안전하게 리소스를 해제하고, Kubernetes Rolling Update 시에도 정상 종료됩니다.

```java
package com.example.kafka.listener;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class MultiThreadedKafkaListener implements MessageListener<String, String> {

    private final ThreadPoolTaskExecutor executor;

    @Autowired
    public MultiThreadedKafkaListener(ThreadPoolTaskExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void onMessage(ConsumerRecord<String, String> record) {
        executor.submit(() -> processMessage(record));
    }

    private void processMessage(ConsumerRecord<String, String> record) {
        System.out.printf("Thread [%s] processing message: key=%s, value=%s%n",
                Thread.currentThread().getName(),
                record.key(),
                record.value());
        // 비즈니스 로직 처리
    }
}
```

---

### ✅ **스레드풀 Bean 설정 추가 (Spring 관리)**

```java
package com.example.kafka.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class KafkaExecutorConfig {

    @Bean(name = "kafkaExecutor")
    public ThreadPoolTaskExecutor kafkaExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);        // 기본 스레드 수
        executor.setMaxPoolSize(20);         // 최대 스레드 수
        executor.setQueueCapacity(500);      // 대기 큐 크기
        executor.setThreadNamePrefix("kafka-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true); // 안전한 종료
        executor.initialize();
        return executor;
    }
}
```
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

## ✅ 1. **Kafka Listener 설정 (application.yml)**

```yaml
spring:
  kafka:
    consumer:
      bootstrap-servers: your-kafka-broker:9092
      group-id: my-consumer-group
      auto-offset-reset: earliest
      enable-auto-commit: false
    listener:
      concurrency: 5   # 각 서버에서 5개의 Consumer 스레드 사용
```

* **concurrency=5** → Spring Kafka 컨테이너가 자동으로 5개의 스레드를 생성해 파티션을 병렬 처리.

---

## ✅ 2. **스레드풀 Bean 구성 (ExecutorConfig.java)**

> Kafka 컨테이너 스레드와 별개로, **비즈니스 로직 실행용 스레드풀**을 분리하여 안정적 운영.

```java
package com.example.kafka.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ExecutorConfig {

    @Bean(name = "bizExecutor")
    public ThreadPoolTaskExecutor bizExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);         // 비즈니스 로직 병렬 처리 기본 스레드
        executor.setMaxPoolSize(20);          // 최대 스레드 수
        executor.setQueueCapacity(1000);      // 대기열 제한
        executor.setThreadNamePrefix("biz-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true); 
        executor.initialize();
        return executor;
    }
}
```

---

## ✅ 3. **Kafka Listener 구현 (MultiThreadedKafkaListener.java)**

> Kafka 메시지를 **컨테이너 스레드 → 비즈니스 스레드풀**로 안전하게 위임.

```java
package com.example.kafka.listener;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class MultiThreadedKafkaListener {

    private final ThreadPoolTaskExecutor bizExecutor;

    public MultiThreadedKafkaListener(@Qualifier("bizExecutor") ThreadPoolTaskExecutor bizExecutor) {
        this.bizExecutor = bizExecutor;
    }

    @KafkaListener(topics = "my-topic", concurrency = "5")
    public void listen(ConsumerRecord<String, String> record) {
        // Kafka 컨테이너 스레드에서 메시지를 수신 → 비즈니스 스레드풀로 위임
        bizExecutor.submit(() -> processMessage(record));
    }

    private void processMessage(ConsumerRecord<String, String> record) {
        System.out.printf("Thread [%s] processing message: key=%s, value=%s%n",
                Thread.currentThread().getName(),
                record.key(),
                record.value());

        // ✅ 비즈니스 로직 처리
        try {
            Thread.sleep(200); // 예시: 처리 지연
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

---

## ✅ 4. **이 구성의 장점**

* ✅ **Kafka concurrency(5)** → 서버당 5개의 Kafka Consumer 스레드 생성 (파티션 자동 분배).
* ✅ **ThreadPoolTaskExecutor(bizExecutor)** → 메시지 처리 로직을 별도 풀에서 비동기 처리하여 **컨슈머 스레드가 블로킹되지 않음**.
* ✅ **스레드풀 안전 종료 지원** → Rolling Update 시 기존 작업 완료 후 종료.
* ✅ **메모리 폭증 방지** → `queueCapacity` 제한 + Spring이 관리하는 Executor.

---

## ✅ 5. **5대 서버 배포 시 동작**

* 5대 서버 × concurrency(5) → 총 25개의 Consumer 스레드.
* Kafka 파티션이 최소 25개 이상이면 **완벽한 병렬 처리** 가능.
* 처리 로직이 오래 걸려도 컨슈머 스레드가 막히지 않으므로 **메모리 증가 없이 안정적 배포 가능**.
