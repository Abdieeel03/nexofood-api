package lat.nexofood.api.modules.auth.dto.response;

import lat.nexofood.api.modules.auth.domain.UserSystemRole;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        UserSystemRole systemRole,
        Boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
