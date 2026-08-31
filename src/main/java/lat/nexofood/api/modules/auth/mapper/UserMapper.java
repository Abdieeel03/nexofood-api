package lat.nexofood.api.modules.auth.mapper;

import lat.nexofood.api.modules.auth.domain.User;
import lat.nexofood.api.modules.auth.dto.request.UserRegisterRequest;
import lat.nexofood.api.modules.auth.dto.response.UserResponse;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .systemRole(user.getSystemRole())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    public User toEntity(UserRegisterRequest request, String passwordHash) {
        if (request == null) {
            return null;
        }
        return User.builder()
                .email(request.email())
                .passwordHash(passwordHash)
                .fullName(request.fullName())
                .phone(request.phone())
                .build();
    }
}
