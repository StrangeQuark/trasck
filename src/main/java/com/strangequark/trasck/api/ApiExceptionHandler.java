package com.strangequark.trasck.api;

import com.strangequark.trasck.config.RuntimeSecurityProfile;
import com.strangequark.trasck.security.SecretTextRedactor;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final RuntimeSecurityProfile runtimeSecurityProfile;
    private final SecretTextRedactor secretTextRedactor;

    public ApiExceptionHandler(RuntimeSecurityProfile runtimeSecurityProfile, SecretTextRedactor secretTextRedactor) {
        this.runtimeSecurityProfile = runtimeSecurityProfile;
        this.secretTextRedactor = secretTextRedactor;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException exception, HttpServletRequest request) {
        HttpStatusCode status = exception.getStatusCode();
        if (status.is5xxServerError()) {
            logServerError(exception, request);
        }
        return ResponseEntity.status(status).body(errorResponse(
                status,
                exception.getReason(),
                request
        ));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid request",
                request
        ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse(
                HttpStatus.FORBIDDEN,
                "Forbidden",
                request
        ));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(errorResponse(
                HttpStatus.METHOD_NOT_ALLOWED,
                "Method not allowed",
                request
        ));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(errorResponse(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported media type",
                request
        ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUpload(MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(errorResponse(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Upload exceeds the configured limit",
                request
        ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NoResourceFoundException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse(
                HttpStatus.NOT_FOUND,
                "Resource not found",
                request
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        logServerError(exception, request);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                exception.getMessage(),
                request
        ));
    }

    private ApiErrorResponse errorResponse(HttpStatusCode status, String reason, HttpServletRequest request) {
        HttpStatus httpStatus = HttpStatus.resolve(status.value());
        String error = httpStatus == null ? "HTTP " + status.value() : httpStatus.getReasonPhrase();
        String message = reason == null || reason.isBlank() ? error : reason;
        if (runtimeSecurityProfile.isProductionLike() && status.is5xxServerError()) {
            message = "Request failed";
        }
        return new ApiErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC),
                status.value(),
                error,
                secretTextRedactor.redact(message),
                request == null ? null : secretTextRedactor.redact(request.getRequestURI())
        );
    }

    private void logServerError(Exception exception, HttpServletRequest request) {
        String path = request == null ? "unknown" : secretTextRedactor.redact(request.getRequestURI());
        if (runtimeSecurityProfile.isProductionLike()) {
            LOGGER.error(
                    "API request failed at {}: {}",
                    path,
                    secretTextRedactor.redact(exception.toString())
            );
            return;
        }
        LOGGER.error("API request failed at {}", path, exception);
    }
}
