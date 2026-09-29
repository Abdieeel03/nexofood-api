package lat.nexofood.api.modules.store.application.service.auth;

import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.identity.domain.RefreshToken;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.infrastructure.repository.RefreshTokenRepository;
import lat.nexofood.api.modules.identity.infrastructure.repository.UserRepository;
import lat.nexofood.api.modules.identity.web.dto.response.UserResponse;
import lat.nexofood.api.modules.identity.web.mapper.UserMapper;
import lat.nexofood.api.modules.store.application.usecase.auth.StoreRegisterUseCase;
import lat.nexofood.api.modules.store.web.dto.request.StoreRegisterRequest;
import lat.nexofood.api.modules.store.web.dto.response.StoreAuthResponse;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantCustomerRepository;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.store.web.mapper.TenantCustomerMapper;
import lat.nexofood.api.common.security.TokenHashUtil;
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
public class StoreRegisterService implements StoreRegisterUseCase {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final TenantCustomerRepository tenantCustomerRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final TenantCustomerMapper tenantCustomerMapper;

    @Override
    @Transactional
    public StoreAuthResponse execute(String tenantSlug, StoreRegisterRequest request) {
        // 1. Validar que el restaurante exista y esté activo
        Tenant tenant = tenantRepository.findBySlug(tenantSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado: " + tenantSlug));

        if (!Boolean.TRUE.equals(tenant.getIsActive())) {
            throw new ResourceConflictException("El restaurante no está disponible en este momento");
        }

        // 2. Si el email YA existe → el cliente debe hacer login
        if (userRepository.existsByEmail(request.email())) {
            throw new ResourceConflictException(
                    "Ya existe una cuenta con este email. Por favor, inicia sesión en la tienda.");
        }

        // 3. Crear el User global
        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .phone(request.phone())
                .build();
        User savedUser = userRepository.save(user);

        // 4. Crear el TenantCustomer para este tenant
        TenantCustomer tenantCustomer = TenantCustomer.builder()
                .tenant(tenant)
                .user(savedUser)
                .build();
        TenantCustomer savedCustomer = tenantCustomerRepository.save(tenantCustomer);

        // 5. Emitir tokens JWT
        String accessToken = jwtService.generateToken(savedUser);
        String refreshTokenStr = jwtService.generateRefreshToken(savedUser);
        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(TokenHashUtil.hash(refreshTokenStr))
                .user(savedUser)
                .expiryDate(LocalDateTime.now().plus(jwtService.getRefreshExpirationTime(), ChronoUnit.MILLIS))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        log.info("Cliente registrado en tienda '{}' con userId: {}", tenantSlug, savedUser.getId());

        return StoreAuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationTime() / 1000)
                .user(userMapper.toResponse(savedUser))
                .customerProfile(tenantCustomerMapper.toResponse(savedCustomer))
                .build();
    }
}
