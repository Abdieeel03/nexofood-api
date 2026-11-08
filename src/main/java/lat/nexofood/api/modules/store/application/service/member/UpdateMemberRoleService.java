package lat.nexofood.api.modules.store.application.service.member;

import lat.nexofood.api.common.audit.AuditAction;
import lat.nexofood.api.common.audit.SecurityAuditLogger;
import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ForbiddenException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.security.TenantAccessService;
import lat.nexofood.api.modules.store.application.usecase.member.UpdateMemberRoleUseCase;
import lat.nexofood.api.modules.store.domain.TenantMember;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantMemberRepository;
import lat.nexofood.api.modules.store.web.dto.response.TenantMemberResponse;
import lat.nexofood.api.modules.store.web.mapper.TenantMemberMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
public class UpdateMemberRoleService implements UpdateMemberRoleUseCase {

    private final TenantMemberRepository tenantMemberRepository;
    private final TenantMemberMapper tenantMemberMapper;
    private final TenantAccessService tenantAccessService;
    private final SecurityAuditLogger securityAuditLogger;

    public UpdateMemberRoleService(
            TenantMemberRepository tenantMemberRepository,
            TenantMemberMapper tenantMemberMapper,
            TenantAccessService tenantAccessService
    ) {
        this(tenantMemberRepository, tenantMemberMapper, tenantAccessService, new SecurityAuditLogger());
    }

    @Autowired
    public UpdateMemberRoleService(
            TenantMemberRepository tenantMemberRepository,
            TenantMemberMapper tenantMemberMapper,
            TenantAccessService tenantAccessService,
            SecurityAuditLogger securityAuditLogger
    ) {
        this.tenantMemberRepository = tenantMemberRepository;
        this.tenantMemberMapper = tenantMemberMapper;
        this.tenantAccessService = tenantAccessService;
        this.securityAuditLogger = securityAuditLogger != null ? securityAuditLogger : new SecurityAuditLogger();
    }

    @Override
    @Transactional
    public TenantMemberResponse execute(UUID tenantId, UUID memberId, TenantStaffRole newRole) {
        TenantMember member = tenantMemberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Miembro no encontrado"));

        if (!member.getTenant().getId().equals(tenantId)) {
            throw new ResourceNotFoundException("El miembro no pertenece a este restaurante");
        }

        if (member.getRole() == TenantStaffRole.OWNER) {
            throw new ForbiddenException("No se puede modificar el rol del propietario (OWNER) del restaurante");
        }

        if (newRole == TenantStaffRole.OWNER && !tenantAccessService.isSuperAdmin()) {
            throw new BadRequestException("No se puede promover un miembro al rol OWNER");
        }

        boolean callerIsOwnerOrSuperAdmin = tenantAccessService.isSuperAdmin() || tenantAccessService.isOwner(tenantId);
        if (!callerIsOwnerOrSuperAdmin
                && (member.getRole() == TenantStaffRole.ADMIN || newRole == TenantStaffRole.ADMIN)) {
            throw new ForbiddenException("Solo el propietario (OWNER) puede gestionar el rol de otros administradores");
        }

        TenantStaffRole previousRole = member.getRole();
        member.setRole(newRole);
        TenantMember updated = tenantMemberRepository.save(member);
        securityAuditLogger.logEvent(
                AuditAction.ROLE_CHANGE,
                tenantId,
                memberId.toString(),
                "Cambio de rol de " + previousRole + " a " + newRole
        );
        log.info("Rol del miembro {} actualizado a: {}", memberId, newRole);
        return tenantMemberMapper.toResponse(updated);
    }
}
