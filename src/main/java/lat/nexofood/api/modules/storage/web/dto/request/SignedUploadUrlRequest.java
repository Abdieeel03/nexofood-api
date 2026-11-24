package lat.nexofood.api.modules.storage.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lat.nexofood.api.modules.storage.domain.StorageTarget;
import lombok.Builder;

@Builder
public record SignedUploadUrlRequest(
        @NotNull(message = "El tipo de destino es obligatorio")
        StorageTarget target,

        @NotBlank(message = "El tipo MIME del archivo es obligatorio")
        String contentType,

        @NotNull(message = "El tamaño del archivo es obligatorio")
        @Positive(message = "El tamaño del archivo debe ser mayor a 0")
        Long fileSizeBytes
) {
}
