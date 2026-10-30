package kr.ject.naspi.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * API에서 발생한 예외를 공통 오류 응답({@link ErrorResponse})으로 변환한다.
 *
 * <p>Controller와 Service는 예외를 직접 처리하지 않고 이곳에 맡긴다.
 * 구체적인 예외일수록 먼저 처리되며, 어디에도 해당하지 않는 예외는 500으로 응답한다.
 *
 * <p>입력값에 비밀번호나 토큰 같은 민감정보가 포함될 수 있으므로 로그에 요청 값을 남기지 않고,
 * 스택 트레이스와 SQL 같은 내부 정보는 응답에 포함하지 않는다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 규칙 위반을 예외에 지정된 오류 유형과 메시지로 응답한다.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e,
                                                                 HttpServletRequest request) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("{} {} -> {} {}: {}", request.getMethod(), request.getRequestURI(),
                errorCode.getStatus().value(), errorCode, e.getMessage());
        return toResponse(ErrorResponse.of(errorCode.getStatus(), e.getMessage(),
                request.getRequestURI(), null));
    }

    /**
     * {@code @Valid} 요청 본문 검증 실패를 400과 필드별 오류 목록으로 응답한다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        List<ErrorResponse.FieldError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldError(error.getField(),
                        error.getDefaultMessage()))
                .toList();
        return invalidInput(request, e, errors);
    }

    /**
     * Controller 메서드 파라미터 제약 조건(예: {@code @Min}) 위반을 400과 파라미터별 오류 목록으로 응답한다.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidation(
            HandlerMethodValidationException e, HttpServletRequest request) {
        List<ErrorResponse.FieldError> errors = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ErrorResponse.FieldError(
                                result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return invalidInput(request, e, errors);
    }

    /**
     * {@code @Validated} 클래스의 메서드 검증 실패를 400과 필드별 오류 목록으로 응답한다.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException e, HttpServletRequest request) {
        List<ErrorResponse.FieldError> errors = e.getConstraintViolations().stream()
                .map(violation -> new ErrorResponse.FieldError(lastNodeName(violation),
                        violation.getMessage()))
                .toList();
        return invalidInput(request, e, errors);
    }

    /**
     * 경로 변수나 요청 파라미터의 타입이 맞지 않는 요청을 400으로 응답한다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        return invalidInput(request, e,
                List.of(new ErrorResponse.FieldError(e.getName(), "타입이 올바르지 않습니다.")));
    }

    /**
     * 필수 요청 파라미터가 빠진 요청을 400으로 응답한다.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameter(
            MissingServletRequestParameterException e, HttpServletRequest request) {
        return invalidInput(request, e,
                List.of(new ErrorResponse.FieldError(e.getParameterName(), "필수 값입니다.")));
    }

    /**
     * JSON 문법 오류 등으로 읽을 수 없는 요청 본문을 400으로 응답한다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException e, HttpServletRequest request) {
        return warn(request, e, ErrorCode.INVALID_REQUEST);
    }

    /**
     * 존재하지 않는 경로로 들어온 요청을 HTML 오류 페이지 대신 JSON 404로 응답한다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e,
                                                               HttpServletRequest request) {
        return warn(request, e, ErrorCode.NOT_FOUND);
    }

    /**
     * 경로가 지원하지 않는 HTTP 메서드로 들어온 요청을 405로 응답한다.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        return warn(request, e, ErrorCode.METHOD_NOT_ALLOWED);
    }

    /**
     * 중복 키나 제약 조건 위반처럼 기존 데이터와 충돌하는 저장 요청을 409로 응답한다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException e, HttpServletRequest request) {
        return error(request, e, ErrorCode.CONFLICT);
    }

    /**
     * 데이터베이스 접근 중 발생한 오류를 SQL 노출 없이 500으로 응답한다.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccess(DataAccessException e,
                                                          HttpServletRequest request) {
        return error(request, e, ErrorCode.INTERNAL_SERVER_ERROR);
    }

    /**
     * 위에서 처리하지 않은 모든 예외를 응답한다.
     *
     * <p>지원하지 않는 Content-Type(415)처럼 클라이언트 잘못으로 Spring이 발생시킨 예외는
     * 원래 4xx 상태를 유지하고, 그 외 예상하지 못한 예외는 500으로 응답한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e, HttpServletRequest request) {
        if (e instanceof org.springframework.web.ErrorResponse springError
                && springError.getStatusCode().is4xxClientError()) {
            HttpStatusCode status = springError.getStatusCode();
            logWarn(request, status, e);
            return toResponse(ErrorResponse.of(status, ErrorCode.INVALID_REQUEST.getMessage(),
                    request.getRequestURI(), null));
        }
        return error(request, e, ErrorCode.INTERNAL_SERVER_ERROR);
    }

    /**
     * 입력값 오류를 400과 상세 오류 목록으로 응답한다.
     */
    private ResponseEntity<ErrorResponse> invalidInput(HttpServletRequest request, Exception e,
                                                       List<ErrorResponse.FieldError> errors) {
        logWarn(request, ErrorCode.INVALID_INPUT.getStatus(), e);
        return toResponse(ErrorResponse.of(ErrorCode.INVALID_INPUT, request.getRequestURI(),
                errors));
    }

    /**
     * 클라이언트 요청 문제로 인한 오류를 경고 로그와 함께 응답한다.
     */
    private ResponseEntity<ErrorResponse> warn(HttpServletRequest request, Exception e,
                                               ErrorCode errorCode) {
        logWarn(request, errorCode.getStatus(), e);
        return toResponse(ErrorResponse.of(errorCode, request.getRequestURI()));
    }

    /**
     * 서버 측 원인 파악이 필요한 오류를 스택 트레이스 로그와 함께 응답한다.
     */
    private ResponseEntity<ErrorResponse> error(HttpServletRequest request, Exception e,
                                                ErrorCode errorCode) {
        log.error("{} {} -> {}", request.getMethod(), request.getRequestURI(),
                errorCode.getStatus().value(), e);
        return toResponse(ErrorResponse.of(errorCode, request.getRequestURI()));
    }

    /**
     * 요청 값 없이 어떤 요청이 어떤 오류로 끝났는지만 경고 로그로 남긴다.
     */
    private void logWarn(HttpServletRequest request, HttpStatusCode status, Exception e) {
        log.warn("{} {} -> {} {}", request.getMethod(), request.getRequestURI(), status.value(),
                e.getClass().getSimpleName());
    }

    /**
     * 응답 본문의 status와 같은 HTTP 상태로 응답을 만든다.
     */
    private ResponseEntity<ErrorResponse> toResponse(ErrorResponse body) {
        return ResponseEntity.status(body.status()).body(body);
    }

    /**
     * 제약 조건을 위반한 필드 이름을 구한다.
     */
    private String lastNodeName(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        return path.substring(path.lastIndexOf('.') + 1);
    }
}
