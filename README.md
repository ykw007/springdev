Spring Boot REST API에서 문자열(String)을 `@RequestBody`로 받는 POST 컨트롤러가 있고, 해당 API를 **개발서버에서 실행**하며, **JUnit 테스트는 로컬에서 원격 API 호출로 테스트**하고 싶으신 경우, 다음과 같이 코드를 구성할 수 있습니다.

---

### ✅ 1. 컨트롤러 (개발서버 실행 중)

```java
// SampleController.java
@RestController
@RequestMapping("/api")
public class SampleController {

    @PostMapping("/echo")
    public ResponseEntity<String> echoMessage(@RequestBody String message) {
        return ResponseEntity.ok("Received: " + message);
    }
}
```

---

### ✅ 2. JUnit 테스트 (로컬에서 실행, 개발 서버에 POST 요청)

```java
// SampleControllerTest.java
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

public class SampleControllerTest {

    private final String DEV_SERVER_URL = "http://your-dev-server.com/api/echo"; // 개발서버 주소

    @Test
    public void testPostStringToDevServer() {
        RestTemplate restTemplate = new RestTemplate();

        String testMessage = "Hello from test!";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN); // 문자열 전송 시 중요

        HttpEntity<String> request = new HttpEntity<>(testMessage, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                DEV_SERVER_URL,
                HttpMethod.POST,
                request,
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Received:"));
    }
}
```

---

### ✅ 주의사항

* **Content-Type**: `MediaType.TEXT_PLAIN`으로 설정해야 `String` 수신이 제대로 됨.
* **CORS 설정**: 개발서버에 테스트 시 IP 접근 허용 및 `@CrossOrigin` 고려.
* **보안**: 인증이 필요한 경우 JWT, BasicAuth 등의 인증 헤더 추가 필요.
* **RestTemplate 대체**: Spring Boot 3 이상에서는 `WebClient` 사용 권장.

---

### 🔄 WebClient 사용 예시 (대안)

```java
WebClient client = WebClient.builder().baseUrl("http://your-dev-server.com").build();

String response = client.post()
    .uri("/api/echo")
    .contentType(MediaType.TEXT_PLAIN)
    .bodyValue("Hello WebClient!")
    .retrieve()
    .bodyToMono(String.class)
    .block();
```

