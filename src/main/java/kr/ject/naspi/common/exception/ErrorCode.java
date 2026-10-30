package kr.ject.naspi.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * API 오류 유형을 정의한다.
 *
 * <p>각 유형은 클라이언트에 응답할 HTTP 상태 코드와 기본 메시지를 가진다.
 * 새로운 오류 유형이 필요하면 이곳에 추가하고 {@link BusinessException}으로 발생시킨다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    /** 요청 형식이 잘못되어 처리할 수 없는 경우. */
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),

    /** 요청 값이 검증 규칙을 통과하지 못한 경우. */
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),

    /** 인증 정보가 없거나 유효하지 않은 경우. */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),

    /** 인증은 되었지만 요청한 작업을 수행할 권한이 없는 경우. */
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    /** 요청한 리소스가 존재하지 않는 경우. */
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),

    /** 해당 경로가 지원하지 않는 HTTP 메서드로 요청한 경우. */
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),

    /** 중복 등록처럼 요청이 기존 데이터와 충돌하는 경우. */
    CONFLICT(HttpStatus.CONFLICT, "요청한 데이터가 기존 데이터와 충돌합니다."),

    /** 서버 내부 문제로 요청을 처리하지 못한 경우. */
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}
