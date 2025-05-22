

##  **더미 서버를 Spring Context 로딩 전에 시작**

Spring의 ApplicationContext가 만들어지기 전에 더미 서버가 먼저 시작되도록 **JVM 레벨에서 제어**해야 합니다.

###  `@TestConfiguration + Initializer`

Spring Boot는 `ContextConfiguration.Initializer`를 통해 **컨텍스트 로딩 전 작업을 수행**할 수 있습니다.

---

## ✅ 추천 방식: `ApplicationContextInitializer`를 활용한 더미 서버 선 실행

### 1. `DummyServerInitializer.java`

```java
public class DummyServerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final int PORT = 9099;
    private static DummyCheckServer dummyServer;

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        // 더미 서버는 최초 한 번만 실행
        if (dummyServer == null) {
            dummyServer = new DummyCheckServer(PORT);
            dummyServer.start();
        }

        // Spring 환경 설정 주입
        TestPropertyValues.of(
            "check.server.url=http://localhost:" + PORT
        ).applyTo(applicationContext.getEnvironment());
    }
}
```

---

### 2. 테스트 클래스

```java
@SpringBootTest
@ContextConfiguration(initializers = DummyServerInitializer.class)
public class MyRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testControllerCheckEndpoint() throws Exception {
        mockMvc.perform(get("/check"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("checked from")));
    }
}
