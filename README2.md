// src/test/java/com/example/controller/AControllerIntegrationTest.java
package com.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * [통합 테스트]
 * Controller -> ServiceImpl -> AMapper -> H2(in-memory DB) 전체 프로세스 검증
 *
 * @ActiveProfiles("test") : application-test.yml 로드
 * @SpringBootTest          : 전체 ApplicationContext 로딩
 * @AutoConfigureMockMvc    : MockMvc 자동 설정 (실제 서블릿 컨테이너 없이 HTTP 요청 시뮬레이션)
 *
 * [변경] 쿼리 파라미터 a=1, b=2 필수 조건 추가
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("[통합 테스트] AController 전체 프로세스")
class AControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────────────
    // 공통 헤더 / 파라미터 상수
    // [변경] PARAM_A, PARAM_B 상수 추가
    // ─────────────────────────────────────────────────────────────
    private static final String VALID_META   = "{\"uuid\":\"fsafasfa\"}";
    private static final String API_ENDPOINT = "/api/items";

    private static final String PARAM_A      = "1";  // [추가] 필수 파라미터 a
    private static final String PARAM_B      = "2";  // [추가] 필수 파라미터 b


    // =========================================================
    // 1. 정상 케이스: meta 헤더 + 파라미터(a=1, b=2) 포함 -> 전체 프로세스 통과
    // [변경] .param("a", PARAM_A).param("b", PARAM_B) 추가
    // =========================================================
    @Test
    @Order(1)
    @DisplayName("정상 요청 - meta 헤더 + a=1, b=2 -> 조회 목록 반환")
    void givenValidMetaHeaderAndParams_whenGetItems_thenReturnList() throws Exception {

        MvcResult result = mockMvc.perform(
                        get(API_ENDPOINT)
                                .header("meta", VALID_META)   // meta 헤더
                                .param("a", PARAM_A)          // [추가] a=1
                                .param("b", PARAM_B)          // [추가] b=2
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("Item A"))
                .andExpect(jsonPath("$[1].name").value("Item B"))
                .andExpect(jsonPath("$[2].name").value("Item C"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        List<?> items = objectMapper.readValue(responseBody, List.class);

        assertThat(items).isNotNull();
        assertThat(items).hasSize(3);

        System.out.println("[OK] a=1, b=2 파라미터 응답 데이터: " + responseBody);
    }


    // =========================================================
    // 2. 에러 케이스: meta 헤더 없음 -> 400 Bad Request
    // [변경] 파라미터 추가 (meta 에러가 먼저 발생하는 케이스 유지)
    // =========================================================
    @Test
    @Order(2)
    @DisplayName("meta 헤더 미포함 -> 400 Bad Request 반환")
    void givenNoMetaHeader_whenGetItems_thenReturn400() throws Exception {

        mockMvc.perform(
                        get(API_ENDPOINT)
                                // meta 헤더 의도적으로 미포함
                                .param("a", PARAM_A)          // [추가] 파라미터는 포함
                                .param("b", PARAM_B)          // [추가]
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("meta header is required"));
    }


    // =========================================================
    // 3. 에러 케이스: meta 헤더에 uuid 키 없음 -> 400 Bad Request
    // [변경] 파라미터 추가
    // =========================================================
    @Test
    @Order(3)
    @DisplayName("meta 헤더에 uuid 누락 -> 400 Bad Request 반환")
    void givenMetaHeaderWithoutUuid_whenGetItems_thenReturn400() throws Exception {

        String invalidMeta = "{\"otherId\":\"12345\"}";  // uuid 키 없음

        mockMvc.perform(
                        get(API_ENDPOINT)
                                .header("meta", invalidMeta)
                                .param("a", PARAM_A)          // [추가]
                                .param("b", PARAM_B)          // [추가]
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("meta.uuid is required"));
    }


    // =========================================================
    // 4. 에러 케이스: meta 헤더가 빈 문자열 -> 400 Bad Request
    // [변경] 파라미터 추가
    // =========================================================
    @Test
    @Order(4)
    @DisplayName("meta 헤더가 빈 문자열 -> 400 Bad Request 반환")
    void givenEmptyMetaHeader_whenGetItems_thenReturn400() throws Exception {

        mockMvc.perform(
                        get(API_ENDPOINT)
                                .header("meta", "")           // 빈 문자열
                                .param("a", PARAM_A)          // [추가]
                                .param("b", PARAM_B)          // [추가]
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("meta header is required"));
    }


    // =========================================================
    // 5. 정상 케이스: uuid 값이 다른 경우도 정상 통과
    // [변경] 파라미터 추가
    // =========================================================
    @Test
    @Order(5)
    @DisplayName("다른 uuid 값으로도 정상 통과")
    void givenDifferentUuid_whenGetItems_thenReturnList() throws Exception {

        String anotherMeta = "{\"uuid\":\"different-uuid-9999\"}";

        mockMvc.perform(
                        get(API_ENDPOINT)
                                .header("meta", anotherMeta)
                                .param("a", PARAM_A)          // [추가]
                                .param("b", PARAM_B)          // [추가]
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3));
    }


    // =========================================================
    // 6. [신규] 에러 케이스: 파라미터 a 누락 -> 400 Bad Request
    // =========================================================
    @Test
    @Order(6)
    @DisplayName("[신규] 파라미터 a 누락 -> 400 Bad Request 반환")
    void givenMissingParamA_whenGetItems_thenReturn400() throws Exception {

        mockMvc.perform(
                        get(API_ENDPOINT)
                                .header("meta", VALID_META)
                                // a 파라미터 의도적으로 누락
                                .param("b", PARAM_B)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andDo(print())
                /*
                 * Controller 에서 @RequestParam(required = true) 로 선언된 경우
                 * -> Spring 이 자동으로 400 반환
                 * Controller 에서 직접 검증하는 경우
                 * -> 해당 에러 메시지로 변경
                 */
                .andExpect(status().isBadRequest());
    }


    // =========================================================
    // 7. [신규] 에러 케이스: 파라미터 b 누락 -> 400 Bad Request
    // =========================================================
    @Test
    @Order(7)
    @DisplayName("[신규] 파라미터 b 누락 -> 400 Bad Request 반환")
    void givenMissingParamB_whenGetItems_thenReturn400() throws Exception {

        mockMvc.perform(
                        get(API_ENDPOINT)
                                .header("meta", VALID_META)
                                .param("a", PARAM_A)
                                // b 파라미터 의도적으로 누락
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andDo(print())
                .andExpect(status().isBadRequest());
    }


    // =========================================================
    // 8. [신규] 에러 케이스: a, b 모두 누락 -> 400 Bad Request
    // =========================================================
    @Test
    @Order(8)
    @DisplayName("[신규] 파라미터 a, b 모두 누락 -> 400 Bad Request 반환")
    void givenMissingBothParams_whenGetItems_thenReturn400() throws Exception {

        mockMvc.perform(
                        get(API_ENDPOINT)
                                .header("meta", VALID_META)
                                // a, b 모두 미포함
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andDo(print())
                .andExpect(status().isBadRequest());
    }


    // =========================================================
    // 9. [신규] 에러 케이스: 파라미터 타입 오류 (a=abc -> NumberFormatException)
    // =========================================================
    @Test
    @Order(9)
    @DisplayName("[신규] 파라미터 타입 오류 (a=abc) -> 400 Bad Request 반환")
    void givenInvalidParamType_whenGetItems_thenReturn400() throws Exception {

        mockMvc.perform(
                        get(API_ENDPOINT)
                                .header("meta", VALID_META)
                                .param("a", "abc")            // 숫자가 아닌 값
                                .param("b", PARAM_B)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andDo(print())
                /*
                 * Controller 파라미터가 int/Integer 타입인 경우
                 * -> MethodArgumentTypeMismatchException -> 400
                 * String 타입인 경우 -> 서비스/쿼리 레이어에서 처리 방식에 따라 다름
                 */
                .andExpect(status().isBadRequest());
    }
}
