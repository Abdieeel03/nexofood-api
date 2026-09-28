package lat.nexofood.api.modules.store.application.service.member;

import lat.nexofood.api.common.constants.ErrorMessages;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.infrastructure.repository.UserRepository;
import lat.nexofood.api.modules.store.application.usecase.member.AddTenantMemberUseCase;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantMember;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantMemberRepository;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.store.web.dto.request.TenantMemberRequest;
import lat.nexofood.api.modules.store.web.dto.response.TenantMemberResponse;
import lat.nexofood.api.modules.store.web.mapper.TenantMemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddTenantMemberService implements AddTenantMemberUseCase {

    private final TenantRepository tenantRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final UserRepository userRepository;
    private final TenantMemberMapper tenantMemberMapper;

    @Override
    @Transactional
    public TenantMemberResponse execute(UUID tenantId, TenantMemberRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado"));

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.USER_NOT_FOUND));

        if (tenantMemberRepository.existsByTenantIdAndUserId(tenantId, request.userId())) {
            throw new ResourceConflictException("El usuario ya es miembro de este restaurante");
        }

        TenantMember member = tenantMemberMapper.toEntity(tenant, user, request.role());
        TenantMember saved = tenantMemberRepository.save(member);
        log.info("Miembro agregado al tenant {}: usuario {}", tenantId, request.userId());
        return tenantMemberMapper.toResponse(saved);
    }
}
