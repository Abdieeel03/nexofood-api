package lat.nexofood.api.common.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {

    private String message;

    private String error;

    private Integer status;

    private LocalDateTime timestamp;

    //Variables temporales para depuración

    private String ex;

    private String path;

    private String method;
}
