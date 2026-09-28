package lat.nexofood.api.modules.store.application.service.member;

import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.store.application.usecase.member.UpdateMemberRoleUseCase;
import lat.nexofood.api.modules.store.domain.TenantMember;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantMemberRepository;
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
public class UpdateMemberRoleService implements UpdateMemberRoleUseCase {

    private final TenantMemberRepository tenantMemberRepository;
    private final TenantMemberMapper tenantMemberMapper;

    @Override
    @Transactional
    public TenantMemberResponse execute(UUID tenantId, UUID memberId, TenantStaffRole newRole) {
        TenantMember member = tenantMemberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Miembro no encontrado"));

        if (!member.getTenant().getId().equals(tenantId)) {
            throw new ResourceNotFoundException("El miembro no pertenece a este restaurante");
        }

        member.setRole(newRole);
        TenantMember updated = tenantMemberRepository.save(member);
        log.info("Rol del miembro {} actualizado a: {}", memberId, newRole);
        return tenantMemberMapper.toResponse(updated);
    }
}
