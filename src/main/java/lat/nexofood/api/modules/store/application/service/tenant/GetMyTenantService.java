package lat.nexofood.api.modules.store.application.service.tenant;

import lat.nexofood.api.common.constants.ErrorMessages;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.infrastructure.repository.UserRepository;
import lat.nexofood.api.modules.store.application.usecase.tenant.GetMyTenantUseCase;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantMemberRepository;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.store.web.dto.response.TenantResponse;
import lat.nexofood.api.modules.store.web.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetMyTenantService implements GetMyTenantUseCase {

    private final TenantRepository tenantRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final UserRepository userRepository;
    private final TenantMapper tenantMapper;

    @Override
    @Transactional(readOnly = true)
    public TenantResponse execute(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.USER_NOT_FOUND));

        // Buscar primero como dueño directo
        return tenantRepository.findByOwnerId(user.getId())
                .map(tenantMapper::toResponse)
                .orElseGet(() ->
                        // Si no es dueño, buscar como miembro de staff
                        tenantMemberRepository.findAllByUserId(user.getId())
                                .stream()
                                .findFirst()
                                .map(member -> tenantMapper.toResponse(member.getTenant()))
                                .orElseThrow(() -> new ResourceNotFoundException(
                                        "No se encontró ningún restaurante asociado a este usuario"))
                );
    }
}
