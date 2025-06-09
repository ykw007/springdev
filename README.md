Spring Boot 기반에서 JUnit 5 (`@SpringBootTest`)와 함께 Kafka 메시지를 멀티스레드로 처리하는 `MessageListener`를 테스트하려면 다음과 같은 구성이 일반적입니다:

---

## ✅ 1. 리스너 클래스 (멀티스레드 Kafka Consumer)

```java
package com.example.kafka.listener;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class MultiThreadedKafkaListener implements MessageListener<String, String> {

    private final ExecutorService executorService;

    public MultiThreadedKafkaListener() {
        this.executorService = Executors.newFixedThreadPool(10);
    }

    @Override
    public void onMessage(ConsumerRecord<String, String> record) {
        executorService.submit(() -> processMessage(record));
    }

    public void processMessage(ConsumerRecord<String, String> record) {
        System.out.printf("Thread [%s] processing message: key=%s, value=%s%n",
                Thread.currentThread().getName(),
                record.key(),
                record.value());
        // 비즈니스 로직 처리
    }

    public void shutdown() {
        executorService.shutdown();
    }
}
```

---

## ✅ 2. Spring Boot 기반 JUnit 5 테스트 클래스

```java
package com.example.kafka.listener;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

@SpringBootTest
class MultiThreadedKafkaListenerTest {

    @Autowired
    private MultiThreadedKafkaListener listener;

    @AfterEach
    void cleanUp() {
        listener.shutdown();
    }

    @Test
    void testKafkaMessageProcessingInParallel() throws InterruptedException {
        // 테스트용 더미 메시지 100개 전송
        for (int i = 0; i < 100; i++) {
            ConsumerRecord<String, String> record = new ConsumerRecord<>("test-topic", 0, i, "key-" + i, "value-" + i);
            listener.onMessage(record);
        }

        // 처리 대기 시간 (멀티스레드 작업 완료 대기)
        TimeUnit.SECONDS.sleep(5);
    }
}
```

---

## ✅ 3. `application.yml` 또는 `application.properties` 설정 (Kafka 설정이 필요 없다면 생략 가능)

```yaml
# application.yml
spring:
  kafka:
    consumer:
      group-id: test-group
      auto-offset-reset: earliest
```

※ 실제 Kafka를 사용하지 않으므로 이 설정은 생략 가능하며, `ConsumerRecord`를 수동으로 생성해서 테스트하는 방식입니다.

---

## 🧪 테스트 확인 사항

* 콘솔에 각기 다른 스레드 이름으로 메시지가 병렬 출력됨
* `Thread [pool-1-thread-7] processing message: key=key-99, value=value-99` 형식 확인
* `@SpringBootTest`를 통해 Spring 컨텍스트 내에서 실제 Bean 주입 확인 가능

---

## 🔧 참고

* 병렬 처리 확인을 위해 로그를 모니터링하거나 `CountDownLatch`, `AtomicInteger` 등 동기화 메커니즘을 테스트 코드에 삽입할 수 있습니다.
* 고급 테스트에서는 `Embedded Kafka`를 사용하여 통합 테스트도 가능합니다.

필요하시면 `EmbeddedKafka`를 이용한 실제 Kafka 브로커 기반 테스트 코드도 제공해드릴게요.
