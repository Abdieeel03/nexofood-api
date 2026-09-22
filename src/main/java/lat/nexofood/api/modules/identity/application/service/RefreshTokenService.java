package lat.nexofood.api.modules.identity.application.service;

import lat.nexofood.api.common.constants.ErrorMessages;
import lat.nexofood.api.common.exception.UnauthorizedException;
import lat.nexofood.api.modules.identity.application.usecase.RefreshTokenUseCase;
import lat.nexofood.api.modules.identity.domain.RefreshToken;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.infrastructure.repository.RefreshTokenRepository;
import lat.nexofood.api.modules.identity.web.dto.request.RefreshTokenRequest;
import lat.nexofood.api.modules.identity.web.dto.response.AuthResponse;
import lat.nexofood.api.modules.identity.web.mapper.UserMapper;
import lat.nexofood.api.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService implements RefreshTokenUseCase {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public AuthResponse execute(RefreshTokenRequest request) {
        log.info("Solicitud de renovación de token recibida");

        RefreshToken currentToken = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new UnauthorizedException(ErrorMessages.INVALID_REFRESH_TOKEN));

        User user = currentToken.getUser();

        if (currentToken.isRevoked()) {
            log.warn("Detección de reutilización de refresh token revocado para el usuario: {}", user.getEmail());
            refreshTokenRepository.revokeAllUserTokens(user.getEmail());
            throw new UnauthorizedException(ErrorMessages.TOKEN_REVOKED);
        }

        if (currentToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            log.warn("Refresh token expirado para el usuario: {}", user.getEmail());
            currentToken.setRevoked(true);
            refreshTokenRepository.save(currentToken);
            throw new UnauthorizedException(ErrorMessages.INVALID_REFRESH_TOKEN);
        }

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            log.warn("Intento de refresh token con usuario desactivado: {}", user.getEmail());
            throw new UnauthorizedException(ErrorMessages.USER_INACTIVE);
        }

        // Rotación de token
        currentToken.setRevoked(true);
        refreshTokenRepository.save(currentToken);

        String newAccessToken = jwtService.generateToken(user);
        String newRefreshTokenStr = jwtService.generateRefreshToken(user);

        RefreshToken newRefreshToken = RefreshToken.builder()
                .token(newRefreshTokenStr)
                .user(user)
                .expiryDate(LocalDateTime.now().plus(jwtService.getRefreshExpirationTime(), ChronoUnit.MILLIS))
                .revoked(false)
                .build();

        refreshTokenRepository.save(newRefreshToken);
        log.info("Token renovado exitosamente para el usuario: {}", user.getEmail());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshTokenStr)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationTime() / 1000)
                .user(userMapper.toResponse(user))
                .build();
    }
}
