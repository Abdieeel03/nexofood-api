package lat.nexofood.api.modules.identity.application.service;

import lat.nexofood.api.modules.identity.application.usecase.LoginUseCase;
import lat.nexofood.api.modules.identity.application.usecase.RefreshTokenUseCase;
import lat.nexofood.api.modules.identity.application.usecase.RegisterUseCase;
import lat.nexofood.api.modules.identity.web.dto.request.LoginRequest;
import lat.nexofood.api.modules.identity.web.dto.request.RefreshTokenRequest;
import lat.nexofood.api.modules.identity.web.dto.request.UserRegisterRequest;
import lat.nexofood.api.modules.identity.web.dto.response.AuthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final RegisterUseCase registerUseCase;
    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;

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
}
