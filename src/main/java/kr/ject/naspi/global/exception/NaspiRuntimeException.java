package kr.ject.naspi.global.exception;

/**
 * naspi 애플리케이션에서 발생시키는 모든 런타임 예외의 상위 타입.
 *
 * <p>비즈니스 규칙 위반이 아닌, 서버 측 원인 파악이 필요한 오류(외부 연동 실패, 있을 수 없는 상태 등)에
 * 직접 사용한다. {@link GlobalExceptionHandler}가 HTTP 500과
 * {@link ErrorCode#INTERNAL_SERVER_ERROR}로 응답하며, 예외 메시지는 로그에만 남고 응답에는 포함하지 않는다.
 *
 * <p>클라이언트에게 결과 코드로 알려야 하는 비즈니스 오류는 {@link NaspiBizException}을 사용한다.
 *
 * <pre>{@code
 * throw new NaspiRuntimeException("카카오 사용자 정보 응답에 id가 없습니다.", e);
 * }</pre>
 */
public class NaspiRuntimeException extends RuntimeException {

    /**
     * 로그에 남길 메시지로 예외를 생성한다.
     *
     * @param message 원인 파악용 메시지 (응답에는 노출되지 않는다)
     */
    public NaspiRuntimeException(String message) {
        super(message);
    }

    /**
     * 로그에 남길 메시지와 원인 예외로 예외를 생성한다.
     *
     * @param message 원인 파악용 메시지 (응답에는 노출되지 않는다)
     * @param cause 원인 예외
     */
    public NaspiRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
