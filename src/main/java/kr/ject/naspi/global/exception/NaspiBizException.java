package kr.ject.naspi.global.exception;

import java.util.Objects;
import lombok.Getter;

/**
 * 비즈니스 규칙 위반을 알리는 예외.
 *
 * <p>반드시 {@link ErrorCode#SUCCESS}가 아닌 {@link ErrorCode}를 지정해야 하며,
 * 메시지를 생략하면 {@link ErrorCode}의 기본 메시지를 쓴다.
 * Service 계층에서 HTTP 응답을 직접 만들지 않고 이 예외를 던지면,
 * {@link GlobalExceptionHandler}가 HTTP 200과 결과 코드, 메시지를 담은 응답으로 변환한다.
 *
 * <pre>{@code
 * memberRepository.findById(memberId)
 *         .orElseThrow(() -> new NaspiBizException(ErrorCode.NOT_FOUND));
 * throw new NaspiBizException(ErrorCode.CONFLICT, "이미 가입한 회원입니다.");
 * }</pre>
 */
@Getter
public class NaspiBizException extends NaspiRuntimeException {

    private final ErrorCode errorCode;

    /**
     * 오류 유형의 기본 메시지로 응답하는 예외를 생성한다.
     *
     * @param errorCode 응답할 오류 유형
     */
    public NaspiBizException(ErrorCode errorCode) {
        this(errorCode, Objects.requireNonNull(errorCode, "errorCode").getMessage());
    }

    /**
     * 기본 메시지 대신 상황에 맞는 메시지로 응답하는 예외를 생성한다.
     *
     * @param errorCode 응답할 오류 유형
     * @param message 클라이언트에 전달할 메시지
     */
    public NaspiBizException(ErrorCode errorCode, String message) {
        super(message);
        Objects.requireNonNull(errorCode, "errorCode");
        if (errorCode == ErrorCode.SUCCESS) {
            throw new IllegalArgumentException("성공 코드로는 비즈니스 예외를 만들 수 없습니다.");
        }
        this.errorCode = errorCode;
    }
}
