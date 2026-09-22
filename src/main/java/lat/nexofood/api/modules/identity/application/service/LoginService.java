package lat.nexofood.api.modules.identity.application.service;

import lat.nexofood.api.common.constants.ErrorMessages;
import lat.nexofood.api.common.exception.UnauthorizedException;
import lat.nexofood.api.modules.identity.application.usecase.LoginUseCase;
import lat.nexofood.api.modules.identity.domain.RefreshToken;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.infrastructure.repository.RefreshTokenRepository;
import lat.nexofood.api.modules.identity.infrastructure.repository.UserRepository;
import lat.nexofood.api.modules.identity.web.dto.request.LoginRequest;
import lat.nexofood.api.modules.identity.web.dto.response.AuthResponse;
import lat.nexofood.api.modules.identity.web.mapper.UserMapper;
import lat.nexofood.api.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginService implements LoginUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public AuthResponse execute(LoginRequest request) {
        log.info("Intento de inicio de sesión para el email: {}", request.email());

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException(ErrorMessages.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Contraseña incorrecta para el usuario: {}", request.email());
            throw new UnauthorizedException(ErrorMessages.INVALID_CREDENTIALS);
        }

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            log.warn("Intento de login con cuenta inactiva: {}", request.email());
            throw new UnauthorizedException(ErrorMessages.USER_INACTIVE);
        }

        refreshTokenRepository.revokeAllUserTokens(user.getEmail());

        String accessToken = jwtService.generateToken(user);
        String refreshTokenStr = jwtService.generateRefreshToken(user);

        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenStr)
                .user(user)
                .expiryDate(LocalDateTime.now().plus(jwtService.getRefreshExpirationTime(), ChronoUnit.MILLIS))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);
        log.info("Inicio de sesión exitoso para el usuario con ID: {}", user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationTime() / 1000)
                .user(userMapper.toResponse(user))
                .build();
    }
}
