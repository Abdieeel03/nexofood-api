package lat.nexofood.api.modules.identity.application.service;

import lat.nexofood.api.modules.identity.application.usecase.GetUserProfileUseCase;
import lat.nexofood.api.modules.identity.application.usecase.LoginUseCase;
import lat.nexofood.api.modules.identity.application.usecase.RefreshTokenUseCase;
import lat.nexofood.api.modules.identity.application.usecase.RegisterUseCase;
import lat.nexofood.api.modules.identity.web.dto.request.LoginRequest;
import lat.nexofood.api.modules.identity.web.dto.request.RefreshTokenRequest;
import lat.nexofood.api.modules.identity.web.dto.request.UserRegisterRequest;
import lat.nexofood.api.modules.identity.web.dto.response.AuthResponse;
import lat.nexofood.api.modules.identity.web.dto.response.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final RegisterUseCase registerUseCase;
    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final GetUserProfileUseCase getUserProfileUseCase;

    @Override
    public AuthResponse register(UserRegisterRequest request) {
        return registerUseCase.execute(request);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        return loginUseCase.execute(request);
    }

    @Override
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        return refreshTokenUseCase.execute(request);
    }

    @Override
    public UserProfileResponse getProfile(String email) {
        return getUserProfileUseCase.execute(email);
    }
}

