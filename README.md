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


============================================================================================================================

좋습니다! 아래는 **Spring Boot + JUnit 5** 환경에서 **Embedded Kafka**를 활용해 Kafka 메시지를 실제로 송수신하고, `CountDownLatch` 및 `AtomicInteger`를 사용하여 **멀티스레드 메시지 처리 확인**까지 가능한 전체 테스트 예제입니다.

---

## ✅ 1. Gradle 의존성 (build.gradle)

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter'
    implementation 'org.springframework.kafka:spring-kafka'

    testImplementation 'org.springframework.kafka:spring-kafka-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

---

## ✅ 2. Kafka 멀티스레드 리스너 클래스

```java
package com.example.kafka.listener;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Component
public class MultiThreadedKafkaListener {

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private Consumer<ConsumerRecord<String, String>> messageProcessor;

    public void setMessageProcessor(Consumer<ConsumerRecord<String, String>> processor) {
        this.messageProcessor = processor;
    }

    @KafkaListener(topics = "test-topic", groupId = "test-group")
    public void onMessage(ConsumerRecord<String, String> record) {
        executorService.submit(() -> {
            if (messageProcessor != null) {
                messageProcessor.accept(record);
            } else {
                defaultProcessing(record);
            }
        });
    }

    private void defaultProcessing(ConsumerRecord<String, String> record) {
        System.out.printf("Default Thread [%s] - Key: %s, Value: %s%n",
                Thread.currentThread().getName(), record.key(), record.value());
    }
}
```

---

## ✅ 3. 테스트 코드 (JUnit 5 + Embedded Kafka + CountDownLatch)

```java
package com.example.kafka;

import com.example.kafka.listener.MultiThreadedKafkaListener;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = "test-topic", brokerProperties = {
        "listeners=PLAINTEXT://localhost:9092", "port=9092"
})
class MultiThreadedKafkaListenerEmbeddedTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private MultiThreadedKafkaListener listener;

    private static final int MESSAGE_COUNT = 100;

    private CountDownLatch latch;
    private AtomicInteger processedCount;

    @BeforeEach
    void setup() {
        latch = new CountDownLatch(MESSAGE_COUNT);
        processedCount = new AtomicInteger(0);

        listener.setMessageProcessor(record -> {
            try {
                System.out.printf("Processed by [%s]: key=%s, value=%s%n",
                        Thread.currentThread().getName(), record.key(), record.value());
                processedCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });
    }

    @Test
    @Timeout(10)
    void testKafkaMessagesProcessedConcurrently() throws InterruptedException {
        // 메시지 발행
        for (int i = 0; i < MESSAGE_COUNT; i++) {
            kafkaTemplate.send(new ProducerRecord<>("test-topic", "key-" + i, "value-" + i));
        }

        // 모든 메시지가 처리될 때까지 대기
        boolean completed = latch.await(10, TimeUnit.SECONDS);

        // 검증
        assertThat(completed).isTrue();
        assertThat(processedCount.get()).isEqualTo(MESSAGE_COUNT);
    }
}
```

---

## ✅ 요약

| 구성 요소                 | 설명                   |
| --------------------- | -------------------- |
| `@EmbeddedKafka`      | 테스트용 내장 Kafka 브로커 구동 |
| `KafkaTemplate`       | 메시지 발행               |
| `CountDownLatch`      | 모든 메시지 수신 대기         |
| `AtomicInteger`       | 병렬 처리된 메시지 수 카운팅     |
| `setMessageProcessor` | 테스트 목적 커스텀 메시지 처리 등록 |

---

## ✅ 참고

* 위 코드는 `test-topic`을 사용하며, 메시지는 `key-0 ~ key-99`로 발송됩니다.
* 실제 운영과 유사한 병렬 처리를 확인할 수 있으며, `Thread 이름`을 통해 병렬성 검증이 가능합니다.

원하시면 `KafkaProducer`에 대해 transactional 설정이나 성능 테스트용 확장 예제도 제공해드릴 수 있습니다.
