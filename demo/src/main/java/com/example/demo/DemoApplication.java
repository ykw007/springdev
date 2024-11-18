package com.example.demo;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
 
@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

}
/*
Spring Boot 애플리케이션을 경량화하여 빠르게 기동하는 방법은 여러 가지가 있습니다. 주요 최적화 방법을 정리하면 다음과 같습니다.

### 1. **의존성 최소화**
   - 필요한 라이브러리만 포함하여 불필요한 의존성을 제거합니다. Spring Boot Starter로 제공되는 의존성 중 실제로 사용하지 않는 것이 있는지 확인하고, 줄이면 애플리케이션 크기와 기동 시간이 줄어듭니다.

### 2. **Spring Boot 시작 클래스 최적화**
   - `@SpringBootApplication` 클래스에 사용하지 않는 자동 구성(ex: JPA, Web 등)을 제외하여 불필요한 설정을 피할 수 있습니다.
   ```java
   @SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
   ```

### 3. **레이어드 아키텍처 조정**
   - 기동 시 로딩해야 할 서비스, 리포지토리 등의 빈을 필요한 부분으로 제한합니다. 예를 들어, 꼭 필요하지 않은 모듈이나 서비스는 따로 분리하거나 프로파일을 지정하여 특정 프로파일로만 로드되도록 합니다.

### 4. **빈 로딩 최적화**
   - `@Lazy` 어노테이션을 사용해 초기 기동 시점에 꼭 필요하지 않은 빈들은 필요한 시점에 로드되도록 지연 로딩합니다.

### 5. **Spring Native 사용**
   - Spring Native는 Spring Boot 애플리케이션을 GraalVM을 사용해 네이티브 이미지로 변환해 주므로 경량화 및 기동 시간 최적화에 효과적입니다. 다만 네이티브 이미지로 컴파일 시 추가 설정이 필요합니다.

### 6. **JVM 튜닝**
   - `-Xms`, `-Xmx`, `-XX:+UseSerialGC` 등의 JVM 옵션을 통해 메모리 사용량을 조정하고, 가비지 컬렉션을 최적화하여 빠른 기동을 도모할 수 있습니다.

### 7. **캐싱 및 준비된 리소스 활용**
   - 빈 생성을 줄이기 위해 자주 사용되는 데이터는 캐시 메커니즘을 사용하고, 애플리케이션 기동 시 준비된 리소스를 활용해 처리량을 높입니다.

### 8. **DevTools 비활성화**
   - 개발 환경에서 사용하는 DevTools는 프로덕션에서는 필요하지 않으므로 `spring-boot-devtools` 의존성을 제외하거나 비활성화하여 기동 시간을 줄입니다.

이러한 방법들을 통해 Spring Boot 애플리케이션의 기동 시간을 줄이고 더 경량화된 구동 환경을 만들 수 있습니다.



Spring Boot 애플리케이션에서 HikariCP를 이용하여 초경량으로 DB 연결만 수행하려면, 불필요한 기능을 제거하고 필수적인 DB 연결 설정만 남기는 방식으로 최적화할 수 있습니다. 이를 위해 HikariCP와 관련된 최소한의 설정과 빠른 기동을 위한 옵션을 추가할 수 있습니다.

### 설정 방법

#### 1. **HikariCP를 이용한 최소 DB 연결 설정**
   - `application.properties`나 `application.yml`에 HikariCP 설정을 최소한으로 추가합니다. HikariCP는 기본적으로 Spring Boot 2.x 이상에서 기본 설정되므로, 필요 설정만 추가하면 됩니다.
   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/yourdb
       username: yourusername
       password: yourpassword
       driver-class-name: com.mysql.cj.jdbc.Driver
       hikari:
         maximum-pool-size: 5              # 커넥션 풀 크기 최소화
         minimum-idle: 1                   # 최소 유휴 커넥션 수 최소화
         idle-timeout: 10000               # 유휴 커넥션 유지 시간(ms)
         connection-timeout: 2000          # 커넥션 획득 대기 시간(ms)
         pool-name: "HikariPool"           # 풀 이름 설정
   ```

#### 2. **불필요한 자동 설정 제외하기**
   - DB 연결만 필요하므로, Web, Security, JPA 등 불필요한 기능을 비활성화하여 경량화합니다.
   ```java
   @SpringBootApplication(exclude = {
       WebMvcAutoConfiguration.class,
       SecurityAutoConfiguration.class,
       HibernateJpaAutoConfiguration.class
   })
   ```

#### 3. **Lazy Initialization 활성화**
   - `spring.main.lazy-initialization=true`를 추가하여 필요할 때만 빈을 로딩하도록 설정하여, 초기 기동 시 불필요한 리소스를 최소화합니다.
   ```properties
   spring.main.lazy-initialization=true
   ```

#### 4. **CommandLineRunner로 DB 연결만 테스트 후 종료**
   - 단순 DB 연결만 확인하고 기동 후 종료하려면 `CommandLineRunner`를 사용하여 DB 연결 테스트 후 애플리케이션을 종료할 수 있습니다.
   ```java
   @SpringBootApplication
   public class Application implements CommandLineRunner {
       @Autowired
       private DataSource dataSource;

       public static void main(String[] args) {
           SpringApplication.run(Application.class, args);
       }

       @Override
       public void run(String... args) throws Exception {
           try (Connection conn = dataSource.getConnection()) {
               System.out.println("DB Connection Successful: " + conn.isValid(2));
           } catch (SQLException e) {
               e.printStackTrace();
           }
           System.exit(0);  // 애플리케이션 종료
       }
   }
   ```

#### 5. **JVM 최적화 옵션**
   - 서버 환경에 맞게 JVM 메모리 및 가비지 컬렉터 설정을 조정하여 최적화할 수 있습니다.
   - 예: `-Xms128m -Xmx128m -XX:+UseSerialGC` (경량화된 애플리케이션에 적합한 GC 설정)

이와 같이 HikariCP와 관련된 최소 설정을 통해 빠르고 경량화된 Spring Boot 애플리케이션을 구성할 수 있습니다.

import java.util.List;
import java.util.concurrent.*;

public class ParallelProcessExecutor {

    // 실행 중인 프로세스를 추적하는 ConcurrentHashMap
    private static final ConcurrentHashMap<String, Process> runningProcesses = new ConcurrentHashMap<>();

    public static void main(String[] args) throws InterruptedException {
        // 명령어 목록
        List<String> commands = List.of(
            "ping -c 2 google.com",
            "ping -c 2 yahoo.com",
            "ping -c 2 bing.com",
            "ping -c 2 duckduckgo.com",
            "ping -c 2 github.com",
            "ping -c 2 stackoverflow.com",
            "ping -c 2 openai.com",
            "ping -c 2 amazon.com",
            "ping -c 2 netflix.com",
            "ping -c 2 reddit.com"
        );

        // 스레드 풀 생성 (최대 10개의 병렬 작업)
        ExecutorService executorService = Executors.newFixedThreadPool(10);

        try {
            // 명령어를 병렬로 실행
            for (String command : commands) {
                executorService.submit(() -> executeProcess(command));
            }
        } finally {
            // 스레드 풀 종료
            executorService.shutdown();
            executorService.awaitTermination(10, TimeUnit.MINUTES);
        }

        System.out.println("All processes have been executed.");
    }

    // 프로세스를 실행하는 메서드
    private static void executeProcess(String command) {
        if (runningProcesses.containsKey(command)) {
            System.out.println("Command is already running: " + command);
            return;
        }

        try {
            // 프로세스를 시작
            Process process = new ProcessBuilder(command.split(" ")).start();
            runningProcesses.put(command, process);
            System.out.println("Started process: " + command);

            // 비동기적으로 종료 감지
            process.waitFor();
            System.out.println("Finished process: " + command);

        } catch (Exception e) {
            System.err.println("Error executing command: " + command);
            e.printStackTrace();
        } finally {
            // 프로세스 종료 시 목록에서 제거
            runningProcesses.remove(command);
        }
    }
}

*/
