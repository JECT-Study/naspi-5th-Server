package kr.ject.naspi.global.exception;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반을 알리는 예외.
 *
 * <p>Service 계층에서 HTTP 응답을 직접 만들지 않고 이 예외를 던지면,
 * {@link GlobalExceptionHandler}가 HTTP 200과 {@link ErrorCode}의 결과 코드를 담은 응답으로 변환한다.
 *
 * <pre>{@code
 * userRepository.findById(userId)
 *         .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
 * }</pre>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * 오류 유형의 기본 메시지로 응답하는 예외를 생성한다.
     *
     * @param errorCode 응답할 오류 유형
     */
    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    /**
     * 기본 메시지 대신 상황에 맞는 메시지로 응답하는 예외를 생성한다.
     *
     * @param errorCode 응답할 오류 유형
     * @param message 클라이언트에 전달할 메시지
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
