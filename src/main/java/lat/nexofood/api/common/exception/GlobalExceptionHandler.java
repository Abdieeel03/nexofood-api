package lat.nexofood.api.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import lat.nexofood.api.common.response.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

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
}
