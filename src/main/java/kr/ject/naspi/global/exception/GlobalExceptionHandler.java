package kr.ject.naspi.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import kr.ject.naspi.common.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * API에서 발생한 예외를 공통 응답({@link Result})으로 변환한다.
 *
 * <p>Controller와 Service는 예외를 직접 처리하지 않고 이곳에 맡긴다.
 * 구체적인 예외일수록 먼저 처리되며, 어디에도 해당하지 않는 예외는 500으로 응답한다.
 *
 * <p>비즈니스 규칙 위반({@link NaspiBizException})은 정상 처리된 요청으로 보고 HTTP 200에
 * 결과 코드로 오류를 알린다. 그 외 요청 형식 오류나 서버 오류는 해당 HTTP 상태로 응답한다.
 *
 * <p>입력값에 비밀번호나 토큰 같은 민감정보가 포함될 수 있으므로 로그에 요청 값을 남기지 않고,
 * 스택 트레이스와 SQL 같은 내부 정보는 응답에 포함하지 않는다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 규칙 위반을 HTTP 200과 예외에 지정된 결과 코드, 메시지로 응답한다.
     */
    @ExceptionHandler(NaspiBizException.class)
    public ResponseEntity<Result<Void>> handleNaspiBizException(
            NaspiBizException e, HttpServletRequest request) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("{} {} -> {} {}: {}", request.getMethod(), request.getRequestURI(),
                errorCode.getCode(), errorCode, e.getMessage());
        return ResponseEntity.ok(Result.error(errorCode, e.getMessage()));
    }

    /**
     * {@code @Valid} 요청 본문 검증 실패를 400과 필드별 오류 목록으로 응답한다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<List<Result.FieldError>>>
            handleMethodArgumentNotValid(MethodArgumentNotValidException e,
                                         HttpServletRequest request) {
        List<Result.FieldError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new Result.FieldError(error.getField(),
                        error.getDefaultMessage()))
                .toList();
        return invalidInput(request, e, errors);
    }

    /**
     * Controller 메서드 파라미터 제약 조건(예: {@code @Min}) 위반을 400과 파라미터별 오류 목록으로 응답한다.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<List<Result.FieldError>>>
            handleHandlerMethodValidation(HandlerMethodValidationException e,
                                          HttpServletRequest request) {
        List<Result.FieldError> errors = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new Result.FieldError(
                                result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return invalidInput(request, e, errors);
    }

    /**
     * {@code @Validated} 클래스의 메서드 검증 실패를 400과 필드별 오류 목록으로 응답한다.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<List<Result.FieldError>>>
            handleConstraintViolation(ConstraintViolationException e,
                                      HttpServletRequest request) {
        List<Result.FieldError> errors = e.getConstraintViolations().stream()
                .map(violation -> new Result.FieldError(lastNodeName(violation),
                        violation.getMessage()))
                .toList();
        return invalidInput(request, e, errors);
    }

    /**
     * 경로 변수나 요청 파라미터의 타입이 맞지 않는 요청을 400으로 응답한다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<List<Result.FieldError>>>
            handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e,
                                             HttpServletRequest request) {
        return invalidInput(request, e,
                List.of(new Result.FieldError(e.getName(), "타입이 올바르지 않습니다.")));
    }

    /**
     * 필수 요청 파라미터가 빠진 요청을 400으로 응답한다.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<List<Result.FieldError>>>
            handleMissingServletRequestParameter(MissingServletRequestParameterException e,
                                                 HttpServletRequest request) {
        return invalidInput(request, e,
                List.of(new Result.FieldError(e.getParameterName(), "필수 값입니다.")));
    }

    /**
     * JSON 문법 오류 등으로 읽을 수 없는 요청 본문을 400으로 응답한다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException e, HttpServletRequest request) {
        return warn(request, e, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST);
    }

    /**
     * 위에서 처리하지 않은 모든 예외를 응답한다.
     *
     * <p>존재하지 않는 경로(404), 지원하지 않는 HTTP 메서드(405), Content-Type(415)처럼
     * 클라이언트 잘못으로 Spring이 발생시킨 예외({@link ErrorResponse})는 원래 4xx 상태로 응답한다.
     * DB 접근·제약 조건 위반, {@link NaspiRuntimeException} 등 그 외 예외는 내부 정보 노출 없이 500으로 응답한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e,
                                                        HttpServletRequest request) {
        if (e instanceof ErrorResponse springError
                && springError.getStatusCode().is4xxClientError()) {
            HttpStatusCode status = springError.getStatusCode();
            return warn(request, e, status, toErrorCode(status));
        }
        return internalError(request, e);
    }

    /**
     * Spring이 알려준 4xx 상태에 맞는 결과 코드를 구한다.
     */
    private ErrorCode toErrorCode(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            default -> ErrorCode.INVALID_REQUEST;
        };
    }

    /**
     * 입력값 오류를 400과 상세 오류 목록으로 응답한다.
     */
    private ResponseEntity<Result<List<Result.FieldError>>> invalidInput(
            HttpServletRequest request, Exception e, List<Result.FieldError> errors) {
        logWarn(request, HttpStatus.BAD_REQUEST, e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(ErrorCode.INVALID_INPUT, errors));
    }

    /**
     * 클라이언트 요청 문제로 인한 오류를 경고 로그와 함께 응답한다.
     */
    private ResponseEntity<Result<Void>> warn(HttpServletRequest request, Exception e,
                                              HttpStatusCode status, ErrorCode errorCode) {
        logWarn(request, status, e);
        return ResponseEntity.status(status).body(Result.error(errorCode));
    }

    /**
     * 서버 측 원인 파악이 필요한 오류를 스택 트레이스 로그와 함께 응답한다.
     */
    private ResponseEntity<Result<Void>> error(HttpServletRequest request, Exception e,
                                               HttpStatusCode status, ErrorCode errorCode) {
        log.error("{} {} -> {}", request.getMethod(), request.getRequestURI(), status.value(), e);
        return ResponseEntity.status(status).body(Result.error(errorCode));
    }

    /**
     * 예상하지 못한 서버 오류를 500과 {@link ErrorCode#INTERNAL_SERVER_ERROR}로 응답한다.
     */
    private ResponseEntity<Result<Void>> internalError(HttpServletRequest request, Exception e) {
        return error(request, e, HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_SERVER_ERROR);
    }

    /**
     * 요청 값 없이 어떤 요청이 어떤 오류로 끝났는지만 경고 로그로 남긴다.
     */
    private void logWarn(HttpServletRequest request, HttpStatusCode status, Exception e) {
        log.warn("{} {} -> {} {}", request.getMethod(), request.getRequestURI(), status.value(),
                e.getClass().getSimpleName());
    }

    /**
     * 제약 조건을 위반한 필드 이름을 구한다.
     */
    private String lastNodeName(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        return path.substring(path.lastIndexOf('.') + 1);
    }
}
