아래는 \*\*Spring Kafka의 `concurrency`\*\*와 \*\*Spring Bean 기반 `ThreadPoolTaskExecutor`\*\*를 혼합하여,
**5대 서버 × 각 서버 5스레드 = 총 25개의 병렬 Consumer**가 최적 동작하도록 구성한 샘플입니다.

---

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
