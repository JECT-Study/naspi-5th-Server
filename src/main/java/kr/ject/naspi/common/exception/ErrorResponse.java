package kr.ject.naspi.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.springframework.http.HttpStatusCode;

/**
 * 모든 API 오류에 공통으로 사용하는 응답 본문.
 *
 * <p>상세 오류가 없으면 응답 JSON에서 errors 필드가 생략된다.
 *
 * @param status HTTP 상태 코드
 * @param message 사용자에게 전달할 오류 메시지
 * @param path 오류가 발생한 요청 경로
 * @param errors 필드 단위 상세 오류 목록
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(int status, String message, String path, List<FieldError> errors) {

    /**
     * 오류 유형의 기본 메시지로 응답을 만든다.
     *
     * @param errorCode 응답할 오류 유형
     * @param path 요청 경로
     * @return 오류 응답
     */
    public static ErrorResponse of(ErrorCode errorCode, String path) {
        return of(errorCode.getStatus(), errorCode.getMessage(), path, null);
    }

    /**
     * 오류 유형의 기본 메시지에 필드 단위 상세 오류를 더해 응답을 만든다.
     *
     * @param errorCode 응답할 오류 유형
     * @param path 요청 경로
     * @param errors 필드 단위 상세 오류 목록
     * @return 오류 응답
     */
    public static ErrorResponse of(ErrorCode errorCode, String path, List<FieldError> errors) {
        return of(errorCode.getStatus(), errorCode.getMessage(), path, errors);
    }

    /**
     * 상태 코드와 메시지를 직접 지정해 응답을 만든다.
     *
     * @param status HTTP 상태 코드
     * @param message 사용자에게 전달할 오류 메시지
     * @param path 요청 경로
     * @param errors 필드 단위 상세 오류 목록, 없으면 {@code null}
     * @return 오류 응답
     */
    public static ErrorResponse of(HttpStatusCode status, String message, String path,
                                   List<FieldError> errors) {
        return new ErrorResponse(status.value(), message, path, errors);
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
