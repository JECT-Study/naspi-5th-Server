package kr.ject.naspi.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * API 오류 유형을 정의한다.
 *
 * <p>각 유형은 HTTP 상태, 클라이언트가 오류를 구분하는 결과 코드, 기본 메시지를 가진다.
 *
 * <p>새로운 오류 유형이 필요하면 이곳에 추가하고 {@link BusinessException}으로 발생시킨다.
 * 비즈니스 예외는 HTTP 상태와 관계없이 200으로 응답하므로, 여기서의 HTTP 상태는
 * 프레임워크 예외 응답과 결과 코드의 분류 기준으로 쓰인다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    /** 요청 형식이 잘못되어 처리할 수 없는 경우. */
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, 40001, "잘못된 요청입니다."),

    /** 요청 값이 검증 규칙을 통과하지 못한 경우. */
    INVALID_INPUT(HttpStatus.BAD_REQUEST, 40002, "입력값이 올바르지 않습니다."),

    /** 인증 정보가 없거나 유효하지 않은 경우. */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, 40101, "인증이 필요합니다."),

    /** 인증은 되었지만 요청한 작업을 수행할 권한이 없는 경우. */
    FORBIDDEN(HttpStatus.FORBIDDEN, 40301, "접근 권한이 없습니다."),

    /** 요청한 리소스가 존재하지 않는 경우. */
    NOT_FOUND(HttpStatus.NOT_FOUND, 40401, "요청한 리소스를 찾을 수 없습니다."),

    /** 해당 경로가 지원하지 않는 HTTP 메서드로 요청한 경우. */
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, 40501, "지원하지 않는 요청 방식입니다."),

    /** 중복 등록처럼 요청이 기존 데이터와 충돌하는 경우. */
    CONFLICT(HttpStatus.CONFLICT, 40901, "요청한 데이터가 기존 데이터와 충돌합니다."),

    /** 서버 내부 문제로 요청을 처리하지 못한 경우. */
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 50001, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final int code;
    private final String message;
}
