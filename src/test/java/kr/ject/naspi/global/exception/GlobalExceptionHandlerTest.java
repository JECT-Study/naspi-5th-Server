package kr.ject.naspi.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Arrays;
import kr.ject.naspi.common.response.CommonResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 예외 유형별로 공통 응답 규격과 HTTP 상태, 결과 코드가 올바르게 반환되는지 검증한다.
 *
 * <p>모든 응답은 code, message, data 형식을 따르고, 비즈니스 오류는 HTTP 200으로 응답한다.
 * 스택 트레이스나 SQL 같은 내부 정보는 노출하지 않아야 한다.
 */
@ActiveProfiles("test")
@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest {

    private static final String SQL = "insert into users (email) values ('secret@test.com')";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("정상 요청은 200과 성공 코드, 데이터를 반환한다")
    void success() throws Exception {
        mockMvc.perform(get("/test/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(CommonResponse.SUCCESS_CODE))
                .andExpect(jsonPath("$.data").value("user-1"));
    }

    @Test
    @DisplayName("요청 본문 필수값 누락 시 400과 필드 오류 목록을 반환한다")
    void missingRequiredField() throws Exception {
        ResultActions result = mockMvc.perform(post("/test/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"not-email\"}"));

        expectError(result, 400, 40002, "입력값이 올바르지 않습니다.")
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.field == 'name')].reason").value("이름은 필수입니다."))
                .andExpect(jsonPath("$.data[?(@.field == 'email')].reason")
                        .value("이메일 형식이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("잘못된 JSON 요청은 400을 반환한다")
    void malformedJson() throws Exception {
        ResultActions result = mockMvc.perform(post("/test/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": "));

        expectError(result, 400, 40001, "잘못된 요청입니다.")
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @DisplayName("파라미터 타입 오류는 400과 해당 파라미터 오류를 반환한다")
    void typeMismatch() throws Exception {
        expectError(mockMvc.perform(get("/test/users/abc")), 400, 40002,
                "입력값이 올바르지 않습니다.")
                .andExpect(jsonPath("$.data[0].field").value("id"))
                .andExpect(jsonPath("$.data[0].reason").value("타입이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("필수 요청 파라미터 누락 시 400을 반환한다")
    void missingRequestParameter() throws Exception {
        expectError(mockMvc.perform(get("/test/search")), 400, 40002,
                "입력값이 올바르지 않습니다.")
                .andExpect(jsonPath("$.data[0].field").value("page"));
    }

    @Test
    @DisplayName("요청 파라미터 제약 위반 시 400과 필드 오류를 반환한다")
    void requestParameterConstraintViolation() throws Exception {
        expectError(mockMvc.perform(get("/test/search").param("page", "0")), 400, 40002,
                "입력값이 올바르지 않습니다.")
                .andExpect(jsonPath("$.data[0].field").value("page"))
                .andExpect(jsonPath("$.data[0].reason").value("페이지는 1 이상이어야 합니다."));
    }

    @Test
    @DisplayName("비즈니스 예외는 200과 ErrorCode의 결과 코드, 지정한 메시지를 반환한다")
    void businessException() throws Exception {
        expectError(mockMvc.perform(get("/test/business")), 200, 40901, "이미 가입된 이메일입니다.");
    }

    @Test
    @DisplayName("비즈니스 예외로 리소스를 찾지 못하면 200과 40401을 반환한다")
    void businessNotFound() throws Exception {
        expectError(mockMvc.perform(get("/test/users/999")), 200, 40401,
                "요청한 리소스를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("매핑되지 않은 경로는 HTML이 아닌 JSON 404를 반환한다")
    void noHandler() throws Exception {
        expectError(mockMvc.perform(get("/no-such-path")), 404, 40401,
                "요청한 리소스를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드는 405를 반환한다")
    void methodNotAllowed() throws Exception {
        expectError(mockMvc.perform(post("/test/users/1")), 405, 40501,
                "지원하지 않는 요청 방식입니다.");
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type은 415와 미분류 결과 코드를 반환한다")
    void unsupportedMediaType() throws Exception {
        expectError(mockMvc.perform(post("/test/users")
                .contentType(MediaType.TEXT_PLAIN)
                .content("name")), 415, 41500, "잘못된 요청입니다.");
    }

    @Test
    @DisplayName("데이터 중복은 409를 반환하고 SQL을 노출하지 않는다")
    void dataIntegrityViolation() throws Exception {
        expectError(mockMvc.perform(get("/test/duplicate")), 409, 40901,
                "요청한 데이터가 기존 데이터와 충돌합니다.")
                .andExpect(content().string(not(containsString("insert"))));
    }

    @Test
    @DisplayName("NullPointerException은 500을 반환하고 내부 정보를 노출하지 않는다")
    void nullPointerException() throws Exception {
        expectError(mockMvc.perform(get("/test/npe")), 500, 50001,
                "서버 내부 오류가 발생했습니다.")
                .andExpect(content().string(not(containsString("NullPointerException"))))
                .andExpect(content().string(not(containsString("secret"))));
    }

    @Test
    @DisplayName("DB 접근 오류는 500을 반환하고 SQL을 노출하지 않는다")
    void dataAccessException() throws Exception {
        expectError(mockMvc.perform(get("/test/db")), 500, 50001,
                "서버 내부 오류가 발생했습니다.")
                .andExpect(content().string(not(containsString("insert"))));
    }

    @Test
    @DisplayName("모든 ErrorCode의 결과 코드는 HTTP 상태 코드로 시작하고 서로 겹치지 않는다")
    void errorCodeFormat() {
        for (ErrorCode errorCode : ErrorCode.values()) {
            assertThat(errorCode.getCode() / 100).as(errorCode.name())
                    .isEqualTo(errorCode.getStatus().value());
            assertThat(errorCode.getCode() % 100).as(errorCode.name()).isPositive();
        }
        assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::getCode))
                .doesNotHaveDuplicates();
    }

    /**
     * 오류 응답이 공통 규격을 따르고 기대한 HTTP 상태와 결과 코드를 반환하는지 검증한다.
     */
    private ResultActions expectError(ResultActions result, int status, int code,
                                      String message) throws Exception {
        return result
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.timestamp").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    record CreateUserRequest(
            @NotBlank(message = "이름은 필수입니다.") String name,
            @Email(message = "이메일 형식이 올바르지 않습니다.") String email) {
    }

    /**
     * 시나리오별 예외를 발생시키기 위한 테스트 전용 API.
     */
    @RestController
    static class TestController {

        @GetMapping("/test/users/{id}")
        CommonResponse<String> findUser(@PathVariable Long id) {
            if (id == 999L) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            return CommonResponse.success("user-" + id);
        }

        @PostMapping("/test/users")
        String createUser(@Valid @RequestBody CreateUserRequest request) {
            return request.name();
        }

        @GetMapping("/test/search")
        String search(@RequestParam @Min(value = 1, message = "페이지는 1 이상이어야 합니다.") int page) {
            return "page-" + page;
        }

        @GetMapping("/test/business")
        String business() {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 가입된 이메일입니다.");
        }

        @GetMapping("/test/duplicate")
        String duplicate() {
            throw new DataIntegrityViolationException(SQL);
        }

        @GetMapping("/test/npe")
        String npe() {
            throw new NullPointerException("secret is null");
        }

        @GetMapping("/test/db")
        String db() {
            throw new DataAccessResourceFailureException(SQL);
        }
    }
}
