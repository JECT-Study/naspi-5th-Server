package kr.ject.naspi.common.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 예외 유형별로 공통 오류 응답 규격과 HTTP 상태가 올바르게 반환되는지 검증한다.
 *
 * <p>모든 오류 응답은 status, message, path가 기대값과 같고
 * 스택 트레이스나 SQL 같은 내부 정보를 노출하지 않아야 한다.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest {

    private static final String SQL = "insert into users (email) values ('secret@test.com')";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("정상 요청은 200과 원래 응답을 반환한다")
    void success() throws Exception {
        mockMvc.perform(get("/test/users/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("user-1"));
    }

    @Test
    @DisplayName("요청 본문 필수값 누락 시 400과 필드 오류 목록을 반환한다")
    void missingRequiredField() throws Exception {
        ResultActions result = mockMvc.perform(post("/test/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"not-email\"}"));

        expectError(result, 400, "입력값이 올바르지 않습니다.", "/test/users")
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].reason").value("이름은 필수입니다."))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')].reason")
                        .value("이메일 형식이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("잘못된 JSON 요청은 400을 반환한다")
    void malformedJson() throws Exception {
        ResultActions result = mockMvc.perform(post("/test/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": "));

        expectError(result, 400, "잘못된 요청입니다.", "/test/users")
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("파라미터 타입 오류는 400과 해당 파라미터 오류를 반환한다")
    void typeMismatch() throws Exception {
        expectError(mockMvc.perform(get("/test/users/abc")), 400, "입력값이 올바르지 않습니다.",
                "/test/users/abc")
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andExpect(jsonPath("$.errors[0].reason").value("타입이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("필수 요청 파라미터 누락 시 400을 반환한다")
    void missingRequestParameter() throws Exception {
        expectError(mockMvc.perform(get("/test/search")), 400, "입력값이 올바르지 않습니다.",
                "/test/search")
                .andExpect(jsonPath("$.errors[0].field").value("page"));
    }

    @Test
    @DisplayName("요청 파라미터 제약 위반 시 400과 필드 오류를 반환한다")
    void requestParameterConstraintViolation() throws Exception {
        expectError(mockMvc.perform(get("/test/search").param("page", "0")), 400,
                "입력값이 올바르지 않습니다.", "/test/search")
                .andExpect(jsonPath("$.errors[0].field").value("page"))
                .andExpect(jsonPath("$.errors[0].reason").value("페이지는 1 이상이어야 합니다."));
    }

    @Test
    @DisplayName("비즈니스 예외는 ErrorCode의 상태와 지정한 메시지를 반환한다")
    void businessException() throws Exception {
        expectError(mockMvc.perform(get("/test/business")), 409, "이미 가입된 이메일입니다.",
                "/test/business");
    }

    @Test
    @DisplayName("존재하지 않는 리소스는 404를 반환한다")
    void notFound() throws Exception {
        expectError(mockMvc.perform(get("/test/users/999")), 404, "요청한 리소스를 찾을 수 없습니다.",
                "/test/users/999");
    }

    @Test
    @DisplayName("매핑되지 않은 경로는 HTML이 아닌 JSON 404를 반환한다")
    void noHandler() throws Exception {
        expectError(mockMvc.perform(get("/no-such-path")), 404, "요청한 리소스를 찾을 수 없습니다.",
                "/no-such-path");
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드는 405를 반환한다")
    void methodNotAllowed() throws Exception {
        expectError(mockMvc.perform(post("/test/users/1")), 405, "지원하지 않는 요청 방식입니다.",
                "/test/users/1");
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type은 415를 반환한다")
    void unsupportedMediaType() throws Exception {
        expectError(mockMvc.perform(post("/test/users")
                .contentType(MediaType.TEXT_PLAIN)
                .content("name")), 415, "잘못된 요청입니다.", "/test/users");
    }

    @Test
    @DisplayName("데이터 중복은 409를 반환하고 SQL을 노출하지 않는다")
    void dataIntegrityViolation() throws Exception {
        expectError(mockMvc.perform(get("/test/duplicate")), 409,
                "요청한 데이터가 기존 데이터와 충돌합니다.", "/test/duplicate")
                .andExpect(content().string(not(containsString("insert"))));
    }

    @Test
    @DisplayName("NullPointerException은 500을 반환하고 내부 정보를 노출하지 않는다")
    void nullPointerException() throws Exception {
        expectError(mockMvc.perform(get("/test/npe")), 500, "서버 내부 오류가 발생했습니다.",
                "/test/npe")
                .andExpect(content().string(not(containsString("NullPointerException"))))
                .andExpect(content().string(not(containsString("secret"))));
    }

    @Test
    @DisplayName("DB 접근 오류는 500을 반환하고 SQL을 노출하지 않는다")
    void dataAccessException() throws Exception {
        expectError(mockMvc.perform(get("/test/db")), 500, "서버 내부 오류가 발생했습니다.",
                "/test/db")
                .andExpect(content().string(not(containsString("insert"))));
    }

    /**
     * 오류 응답이 공통 규격을 따르고 HTTP 상태와 본문의 status가 일치하는지 검증한다.
     */
    private ResultActions expectError(ResultActions result, int status, String message,
                                      String path) throws Exception {
        return result
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.path").value(path))
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
        String findUser(@PathVariable Long id) {
            if (id == 999L) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            return "user-" + id;
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
