package lat.nexofood.api.modules.identity.application.service;

import lat.nexofood.api.modules.identity.web.dto.request.LoginRequest;
import lat.nexofood.api.modules.identity.web.dto.request.RefreshTokenRequest;
import lat.nexofood.api.modules.identity.web.dto.request.UserRegisterRequest;
import lat.nexofood.api.modules.identity.web.dto.response.AuthResponse;
import lat.nexofood.api.modules.identity.web.dto.response.UserProfileResponse;

public interface AuthService {

    AuthResponse register(UserRegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);

    UserProfileResponse getProfile(String email);
}
