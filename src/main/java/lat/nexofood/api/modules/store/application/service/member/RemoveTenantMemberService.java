package lat.nexofood.api.modules.store.application.service.member;

import lat.nexofood.api.common.exception.ForbiddenException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.security.TenantAccessService;
import lat.nexofood.api.modules.store.application.usecase.member.RemoveTenantMemberUseCase;
import lat.nexofood.api.modules.store.domain.TenantMember;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RemoveTenantMemberService implements RemoveTenantMemberUseCase {

    private final TenantMemberRepository tenantMemberRepository;
    private final TenantAccessService tenantAccessService;

    @Override
    @Transactional
    public void execute(UUID tenantId, UUID memberId) {
        TenantMember member = tenantMemberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Miembro no encontrado"));

        if (!member.getTenant().getId().equals(tenantId)) {
            throw new ResourceNotFoundException("El miembro no pertenece a este restaurante");
        }

        if (member.getRole() == TenantStaffRole.OWNER) {
            throw new ForbiddenException("El propietario (OWNER) no puede ser desvinculado del restaurante");
        }

        boolean callerIsOwnerOrSuperAdmin = tenantAccessService.isSuperAdmin() || tenantAccessService.isOwner(tenantId);
        if (!callerIsOwnerOrSuperAdmin && member.getRole() == TenantStaffRole.ADMIN) {
            throw new ForbiddenException("Solo el propietario (OWNER) puede desvincular a un administrador");
        }

        tenantMemberRepository.delete(member);
        log.info("Miembro {} desvinculado del tenant {}", memberId, tenantId);
    }
}
