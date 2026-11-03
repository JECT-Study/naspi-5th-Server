package kr.ject.naspi.common.response;

import kr.ject.naspi.global.exception.ErrorCode;
import org.springframework.http.HttpStatusCode;

/**
 * 모든 API가 공통으로 사용하는 응답 본문.
 *
 * <p>클라이언트는 HTTP 상태가 아닌 {@code code}로 결과를 판단한다.
 * 비즈니스 오류는 HTTP 200으로 응답하므로 {@code code}가 {@link #SUCCESS_CODE}인지 확인해야 한다.
 *
 * <pre>{@code
 * { "code": 20000, "message": "요청이 성공했습니다.", "data": { ... } }
 * { "code": 40901, "message": "이미 가입된 이메일입니다.", "data": null }
 * }</pre>
 *
 * @param code 결과 코드, HTTP 상태 코드 뒤에 두 자리 일련번호를 붙인 값 (예: 40401)
 * @param message 사용자에게 전달할 메시지
 * @param data 응답 데이터, 입력값 오류일 때는 필드별 오류 목록
 * @param <T> 응답 데이터 타입
 */
public record CommonResponse<T>(int code, String message, T data) {

    /** 요청이 성공했을 때의 결과 코드. */
    public static final int SUCCESS_CODE = 20000;

    private static final String SUCCESS_MESSAGE = "요청이 성공했습니다.";

    /**
     * 데이터를 담은 성공 응답을 만든다.
     *
     * @param data 응답 데이터
     * @return 성공 응답
     */
    public static <T> CommonResponse<T> success(T data) {
        return new CommonResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    /**
     * 돌려줄 데이터가 없는 성공 응답을 만든다.
     *
     * @return 성공 응답
     */
    public static CommonResponse<Void> success() {
        return success(null);
    }

    /**
     * 오류 유형의 기본 메시지로 오류 응답을 만든다.
     *
     * @param errorCode 응답할 오류 유형
     * @return 오류 응답
     */
    public static CommonResponse<Void> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.getMessage());
    }

    /**
     * 기본 메시지 대신 상황에 맞는 메시지로 오류 응답을 만든다.
     *
     * @param errorCode 응답할 오류 유형
     * @param message 사용자에게 전달할 메시지
     * @return 오류 응답
     */
    public static CommonResponse<Void> error(ErrorCode errorCode, String message) {
        return new CommonResponse<>(errorCode.getCode(), message, null);
    }

    /**
     * 오류 유형의 기본 메시지에 상세 정보를 더해 오류 응답을 만든다.
     *
     * @param errorCode 응답할 오류 유형
     * @param data 필드별 오류 목록 같은 상세 정보
     * @return 오류 응답
     */
    public static <T> CommonResponse<T> error(ErrorCode errorCode, T data) {
        return new CommonResponse<>(errorCode.getCode(), errorCode.getMessage(), data);
    }

    /**
     * {@link ErrorCode}에 정의되지 않은 HTTP 상태의 오류 응답을 만든다.
     *
     * <p>결과 코드는 일련번호 00을 붙여 미분류 오류임을 나타낸다 (예: 415 → 41500).
     *
     * @param status HTTP 상태 코드
     * @param message 사용자에게 전달할 메시지
     * @return 오류 응답
     */
    public static CommonResponse<Void> error(HttpStatusCode status, String message) {
        return new CommonResponse<>(status.value() * 100, message, null);
    }

    /**
     * 어떤 입력값이 왜 잘못되었는지 알려주는 상세 오류.
     *
     * @param field 잘못된 필드 또는 파라미터 이름
     * @param reason 잘못된 이유
     */
    public record FieldError(String field, String reason) {
    }
}
