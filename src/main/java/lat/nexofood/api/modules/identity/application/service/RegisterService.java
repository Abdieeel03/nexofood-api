package lat.nexofood.api.modules.identity.application.service;

import lat.nexofood.api.common.constants.ErrorMessages;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.modules.identity.application.usecase.RegisterUseCase;
import lat.nexofood.api.modules.identity.domain.RefreshToken;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.infrastructure.repository.RefreshTokenRepository;
import lat.nexofood.api.modules.identity.infrastructure.repository.UserRepository;
import lat.nexofood.api.modules.identity.web.dto.request.UserRegisterRequest;
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
public class RegisterService implements RegisterUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public AuthResponse execute(UserRegisterRequest request) {
        log.info("Iniciando registro para el usuario con email: {}", request.email());

        if (userRepository.existsByEmail(request.email())) {
            throw new ResourceConflictException(ErrorMessages.EMAIL_ALREADY_EXISTS);
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = userMapper.toEntity(request, encodedPassword);
        User savedUser = userRepository.save(user);

        String accessToken = jwtService.generateToken(savedUser);
        String refreshTokenStr = jwtService.generateRefreshToken(savedUser);

        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenStr)
                .user(savedUser)
                .expiryDate(LocalDateTime.now().plus(jwtService.getRefreshExpirationTime(), ChronoUnit.MILLIS))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);
        log.info("Usuario registrado exitosamente con ID: {}", savedUser.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationTime() / 1000)
                .user(userMapper.toResponse(savedUser))
                .build();
    }
}
