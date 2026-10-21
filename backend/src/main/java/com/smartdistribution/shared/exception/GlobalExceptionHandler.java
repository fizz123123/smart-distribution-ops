package com.smartdistribution.shared.exception;

import com.smartdistribution.shared.web.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.getErrorCode();

        return buildResponse(
                errorCode,
                exception.getMessage(),
                List.of(),
                request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<ApiErrorResponse.FieldError> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ApiErrorResponse.FieldError(
                        error.getField(),
                        error.getCode(),
                        error.getDefaultMessage()
                ))
                .toList();

        ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;

        return buildResponse(
                errorCode,
                errorCode.getDefaultMessage(),
                fieldErrors,
                request
        );
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;

        return buildResponse(
                errorCode,
                errorCode.getDefaultMessage(),
                List.of(),
                request
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.MALFORMED_REQUEST;

        return buildResponse(
                errorCode,
                errorCode.getDefaultMessage(),
                List.of(),
                request
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
        ApiErrorResponse.FieldError fieldError = new ApiErrorResponse.FieldError(
                exception.getParameterName(),
                "REQUIRED",
                "缺少必要參數。"
        );

        return buildResponse(
                errorCode,
                errorCode.getDefaultMessage(),
                List.of(fieldError),
                request
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
        ApiErrorResponse.FieldError fieldError = new ApiErrorResponse.FieldError(
                exception.getName(),
                "TYPE_MISMATCH",
                "參數格式不正確。"
        );

        return buildResponse(
                errorCode,
                errorCode.getDefaultMessage(),
                List.of(fieldError),
                request
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFoundException(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.RESOURCE_NOT_FOUND;

        return buildResponse(
                errorCode,
                errorCode.getDefaultMessage(),
                List.of(),
                request
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.METHOD_NOT_ALLOWED;

        return buildResponse(
                errorCode,
                errorCode.getDefaultMessage(),
                List.of(),
                request
        );
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLockingFailureException(
            OptimisticLockingFailureException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.VERSION_CONFLICT;

        return buildResponse(
                errorCode,
                errorCode.getDefaultMessage(),
                List.of(),
                request
        );
    }

    // 資料庫約束是最後防線：應用程式已先檢查常見情況，這裡處理同時操作等漏網的衝突
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolationException(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        log.warn("Data integrity violation", exception);
        ErrorCode errorCode = ErrorCode.DATA_CONFLICT;

        return buildResponse(
                errorCode,
                errorCode.getDefaultMessage(),
                List.of(),
                request
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDeniedException(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.ACCESS_DENIED;

        return buildResponse(
                errorCode,
                errorCode.getDefaultMessage(),
                List.of(),
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error("Unhandled exception", exception);

        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return buildResponse(
                errorCode,
                errorCode.getDefaultMessage(),
                List.of(),
                request
        );
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            ErrorCode errorCode,
            String message,
            List<ApiErrorResponse.FieldError> fieldErrors,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                errorCode.name(),
                message,
                fieldErrors,
                (String) request.getAttribute(RequestIdFilter.TRACE_ID_ATTRIBUTE)
        );

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(response);
    }
}
