package lat.nexofood.api.modules.identity.application.service;

import lat.nexofood.api.common.constants.ErrorMessages;
import lat.nexofood.api.common.exception.UnauthorizedException;
import lat.nexofood.api.common.security.TokenHashUtil;
import lat.nexofood.api.modules.identity.application.usecase.LogoutUseCase;
import lat.nexofood.api.modules.identity.domain.RefreshToken;
import lat.nexofood.api.modules.identity.infrastructure.repository.RefreshTokenRepository;
import lat.nexofood.api.modules.identity.web.dto.request.RefreshTokenRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutService implements LogoutUseCase {

    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    @Transactional
    public void execute(RefreshTokenRequest request) {
        String tokenHash = TokenHashUtil.hash(request.refreshToken());
        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException(ErrorMessages.INVALID_REFRESH_TOKEN));

        token.setRevoked(true);
        refreshTokenRepository.save(token);
        log.info("Sesión cerrada (refresh token revocado) para el usuario: {}", token.getUser().getEmail());
    }
}
