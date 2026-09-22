package lat.nexofood.api.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lat.nexofood.api.common.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDeniedException(AccessDeniedException ex) {
        throw ex;
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<ErrorResponse> handleResourceConflictException(
            ResourceConflictException ex,
            HttpServletRequest req
    ) {
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex,
            HttpServletRequest req
    ) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequestException(
            BadRequestException ex,
            HttpServletRequest req
    ) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedException(
            UnauthorizedException ex,
            HttpServletRequest req
    ) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), req);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(
            BadCredentialsException ex,
            HttpServletRequest req
    ) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Credenciales inválidas", req);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabledException(
            DisabledException ex,
            HttpServletRequest req
    ) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "La cuenta de usuario se encuentra desactivada", req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex,
            HttpServletRequest req
    ) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Error de validación: " + details, req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(
            Exception ex,
            HttpServletRequest req
    ) {
        log.error("Unexpected error occurred", ex);

        ErrorResponse error = ErrorResponse.builder()
                .message("Ocurrió un error interno en el servidor")
                .error(HttpStatus.INTERNAL_SERVER_ERROR.name())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .timestamp(LocalDateTime.now())
                .ex(ex.getMessage())
                .path(req.getRequestURI())
                .method(req.getMethod())
                .build();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String message, HttpServletRequest req) {
        ErrorResponse error = ErrorResponse.builder()
                .message(message)
                .error(status.name())
                .status(status.value())
                .timestamp(LocalDateTime.now())
                .path(req.getRequestURI())
                .method(req.getMethod())
                .build();

        return ResponseEntity.status(status).body(error);
    }
}
