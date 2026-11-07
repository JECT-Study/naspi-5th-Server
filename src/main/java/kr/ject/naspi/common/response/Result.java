package kr.ject.naspi.common.response;

import kr.ject.naspi.global.exception.ErrorCode;

/**
 * 모든 API가 공통으로 사용하는 응답 본문.
 *
 * <p>클라이언트는 HTTP 상태가 아닌 {@code code}로 결과를 판단한다.
 * 비즈니스 오류는 HTTP 200으로 응답하므로 {@code code}가 {@link ErrorCode#SUCCESS}인지 확인해야 한다.
 * 요청 형식 오류나 서버 오류는 해당 HTTP 상태와 함께 같은 형식으로 응답한다.
 *
 * <pre>{@code
 * { "code": "000", "message": "", "data": { ... } }
 * { "code": "102", "message": "이미 가입한 회원입니다.", "data": null }
 * }</pre>
 *
 * @param code 결과 코드, 3자리 문자열 ({@link ErrorCode} 참고)
 * @param message 사용자에게 전달할 메시지
 * @param data 응답 데이터, 입력값 오류일 때는 필드별 오류 목록
 * @param <T> 응답 데이터 타입
 */
public record Result<T>(String code, String message, T data) {

    /**
     * 데이터를 담은 성공 응답을 만든다.
     *
     * @param data 응답 데이터
     * @return 성공 응답
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    /**
     * 돌려줄 데이터가 없는 성공 응답을 만든다.
     *
     * @return 성공 응답
     */
    public static Result<Void> success() {
        return success(null);
    }

    /**
     * 오류 유형의 기본 메시지로 오류 응답을 만든다.
     *
     * @param errorCode 응답할 오류 유형
     * @return 오류 응답
     */
    public static Result<Void> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.getMessage());
    }

    /**
     * 기본 메시지 대신 상황에 맞는 메시지로 오류 응답을 만든다.
     *
     * @param errorCode 응답할 오류 유형
     * @param message 사용자에게 전달할 메시지
     * @return 오류 응답
     */
    public static Result<Void> error(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null);
    }

    /**
     * 오류 유형의 기본 메시지에 상세 정보를 더해 오류 응답을 만든다.
     *
     * @param errorCode 응답할 오류 유형
     * @param data 필드별 오류 목록 같은 상세 정보
     * @return 오류 응답
     */
    public static <T> Result<T> error(ErrorCode errorCode, T data) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), data);
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
