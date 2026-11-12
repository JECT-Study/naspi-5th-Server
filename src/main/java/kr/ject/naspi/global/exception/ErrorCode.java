package kr.ject.naspi.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * API 결과 코드를 정의한다.
 *
 * <p>각 코드는 클라이언트가 결과를 구분하는 3자리 문자열과 기본 메시지를 가진다.
 * 결과 코드는 HTTP 상태와 무관하게 자체 체계를 따른다.
 *
 * <ul>
 *   <li>{@code 000}: 성공 ({@link NaspiBizException}에 쓸 수 없다)</li>
 *   <li>{@code 001~099}: 공통 오류</li>
 *   <li>{@code 1xx} member, {@code 2xx} league, {@code 3xx} market, {@code 4xx} asset,
 *       {@code 5xx} order, {@code 6xx} contents</li>
 *   <li>{@code 999}: 서버 내부 오류</li>
 * </ul>
 *
 * <p>새로운 오류 유형이 필요하면 해당 모듈 대역에 추가하고 {@link NaspiBizException}으로 발생시킨다.
 * 비즈니스 예외는 항상 HTTP 200으로 응답하고, 프레임워크 예외(요청 형식 오류, 404, 500 등)의
 * HTTP 상태는 {@link GlobalExceptionHandler}가 예외 종류에 따라 정한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    /** 요청이 성공한 경우. 성공 응답에는 메시지를 담지 않는다. */
    SUCCESS("000", ""),

    /** 요청 형식이 잘못되어 처리할 수 없는 경우. */
    INVALID_REQUEST("001", "잘못된 요청입니다."),

    /** 요청 값이 검증 규칙을 통과하지 못한 경우. */
    INVALID_INPUT("002", "입력값이 올바르지 않습니다."),

    /** 인증 정보가 없거나 유효하지 않은 경우. */
    UNAUTHORIZED("003", "인증이 필요합니다."),

    /** 인증은 되었지만 요청한 작업을 수행할 권한이 없는 경우. */
    FORBIDDEN("004", "접근 권한이 없습니다."),

    /** 요청한 리소스가 존재하지 않는 경우. */
    NOT_FOUND("005", "요청한 리소스를 찾을 수 없습니다."),

    /** 해당 경로가 지원하지 않는 HTTP 메서드로 요청한 경우. */
    METHOD_NOT_ALLOWED("006", "지원하지 않는 요청 방식입니다."),

    /** 중복 등록처럼 요청이 기존 데이터와 충돌하는 경우. */
    CONFLICT("007", "요청한 데이터가 기존 데이터와 충돌합니다."),

    /** 서버 내부 문제로 요청을 처리하지 못한 경우. */
    INTERNAL_SERVER_ERROR("999", "서버 내부 오류가 발생했습니다."),


    // member (1xx)
    /** 카카오 로그인을 거치지 않아 가입 대기 정보 없이 회원가입을 요청한 경우. */
    SIGNUP_LOGIN_REQUIRED("100", "카카오 로그인 후 가입할 수 있습니다."),

    /** 회원 이름이 1~5자 한글 규칙에 맞지 않는 경우. */
    INVALID_MEMBER_NAME("101", "이름은 1~5자의 한글만 사용할 수 있습니다."),

    /** 이미 가입한 카카오 계정으로 다시 가입하려는 경우. */
    MEMBER_ALREADY_JOINED("102", "이미 가입한 회원입니다."),

    /** 회원을 찾을 수 없는 경우 (탈퇴 등으로 회원 정보가 삭제된 경우 포함). */
    MEMBER_NOT_FOUND("103", "회원을 찾을 수 없습니다."),


    // league (2xx)
    /** 리그 이름이 비어 있거나 12자를 넘는 경우. */
    INVALID_LEAGUE_NAME("200", "리그 이름은 1~12자로 입력해야 합니다."),

    /** 초대 코드에 해당하는 대기실이 없는 경우 (형식이 틀렸거나 이미 시작된 리그 포함). */
    INVITE_CODE_NOT_FOUND("201", "유효하지 않은 초대 코드입니다."),

    /** 이미 입장한 리그에 다시 입장하려는 경우. */
    LEAGUE_ALREADY_JOINED("202", "이미 입장한 리그입니다."),

    /** 참여자와 대기자를 합쳐 10명인 리그에 입장하려는 경우. */
    LEAGUE_FULL("203", "리그 인원이 가득 찼습니다."),

    /** 리그를 찾을 수 없는 경우. */
    LEAGUE_NOT_FOUND("204", "리그를 찾을 수 없습니다."),

    /** 대기실과 진행 중인 리그를 합쳐 이미 5개에 참여 중인데 리그를 생성하거나 입장하려는 경우. */
    LEAGUE_JOIN_LIMIT_EXCEEDED("205", "동시에 참여할 수 있는 리그는 최대 5개입니다."),


    // market (3xx)
    /** 종목 이름이 12자 이하의 글자·숫자·공백 규칙에 맞지 않는 경우. */
    INVALID_STOCK_NAME("300", "종목 이름은 1~12자의 글자, 숫자, 공백만 사용할 수 있습니다."),

    /** 종목 소개가 비어 있거나 50자를 넘는 경우. */
    INVALID_STOCK_DESCRIPTION("301", "종목 소개는 1~50자로 입력해야 합니다."),

    /** 같은 리그에 같은 이름의 종목이 이미 있는 경우. */
    STOCK_NAME_DUPLICATED("302", "이미 사용 중인 종목 이름입니다."),

    /** 이미 이 리그에 종목을 상장한 참여자가 다시 상장하려는 경우. */
    STOCK_ALREADY_LISTED("303", "이미 종목을 상장했습니다."),

    /** 요청한 리그에 해당 종목이 없는 경우. */
    STOCK_NOT_FOUND("304", "종목을 찾을 수 없습니다."),

    /** 본인이 상장하지 않은 종목을 수정하려는 경우. */
    STOCK_NOT_OWNED("305", "본인의 종목만 수정할 수 있습니다."),


    // asset (4xx)
    /** 리그 참여자의 자산이 없는 경우 (리그 시작 전이라 초기 자산이 배분되지 않은 경우 포함). */
    ASSET_NOT_FOUND("400", "자산 정보를 찾을 수 없습니다.");

    private final String code;
    private final String message;
}
