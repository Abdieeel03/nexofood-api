package lat.nexofood.api.modules.store.application.service.member;

import lat.nexofood.api.common.constants.ErrorMessages;
import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ForbiddenException;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.common.security.TenantAccessService;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.infrastructure.repository.UserRepository;
import lat.nexofood.api.modules.notification.application.service.NotificationService;
import lat.nexofood.api.modules.notification.domain.NotificationActionStatus;
import lat.nexofood.api.modules.notification.domain.NotificationType;
import lat.nexofood.api.modules.store.application.usecase.member.AddTenantMemberUseCase;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantInvitation;
import lat.nexofood.api.modules.store.domain.TenantInvitationStatus;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantInvitationRepository;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantMemberRepository;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.store.web.dto.request.TenantMemberRequest;
import lat.nexofood.api.modules.store.web.dto.response.TenantMemberResponse;
import lat.nexofood.api.modules.store.web.mapper.TenantMemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddTenantMemberService implements AddTenantMemberUseCase {

    private final TenantRepository tenantRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final TenantInvitationRepository tenantInvitationRepository;
    private final UserRepository userRepository;
    private final TenantMemberMapper tenantMemberMapper;
    private final TenantAccessService tenantAccessService;
    private final CurrentUserProvider currentUserProvider;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public TenantMemberResponse execute(UUID tenantId, TenantMemberRequest request) {
        if (request.role() == TenantStaffRole.OWNER && !tenantAccessService.isSuperAdmin()) {
            throw new BadRequestException("No se puede asignar el rol OWNER a un empleado");
        }

        if (request.role() == TenantStaffRole.ADMIN
                && !tenantAccessService.isSuperAdmin()
                && !tenantAccessService.isOwner(tenantId)) {
            throw new ForbiddenException("Solo el propietario (OWNER) puede agregar administradores");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado"));

        String normalizedEmail = request.email().trim().toLowerCase();
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .or(() -> userRepository.findByEmail(normalizedEmail))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.USER_NOT_FOUND));

        if (user.getDeletedAt() != null) {
            throw new ResourceNotFoundException(ErrorMessages.USER_NOT_FOUND);
        }

        if (tenantMemberRepository.existsByTenantIdAndUserId(tenantId, user.getId())
                || (tenant.getOwner() != null && tenant.getOwner().getId().equals(user.getId()))) {
            throw new ResourceConflictException("El usuario ya es miembro de este restaurante");
        }

        if (tenantInvitationRepository.existsByTenantIdAndInvitedUserIdAndStatus(
                tenantId, user.getId(), TenantInvitationStatus.PENDING)) {
            throw new ResourceConflictException("Ya existe una invitación pendiente para este usuario en este restaurante");
        }

        User inviter = resolveInviterSafely();
        TenantInvitation invitation = TenantInvitation.builder()
                .tenant(tenant)
                .invitedUser(user)
                .inviter(inviter)
                .invitedEmail(user.getEmail())
                .role(request.role())
                .status(TenantInvitationStatus.PENDING)
                .build();

        TenantInvitation savedInvitation = tenantInvitationRepository.save(invitation);

        if (notificationService != null) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (savedInvitation.getId() != null) {
                metadata.put("invitationId", savedInvitation.getId().toString());
            }
            if (tenant.getId() != null) {
                metadata.put("tenantId", tenant.getId().toString());
            }
            metadata.put("tenantName", tenant.getName());
            metadata.put("role", request.role().name());
            metadata.put("allowedActions", List.of("ACCEPT", "REJECT"));
            if (inviter != null) {
                metadata.put("inviterName", inviter.getFullName());
                metadata.put("inviterEmail", inviter.getEmail());
            }

            String dedupKey = savedInvitation.getId() != null
                    ? "STAFF_INVITATION:" + savedInvitation.getId()
                    : null;

            notificationService.createNotification(
                    user,
                    tenant,
                    NotificationType.STAFF_INVITATION,
                    "Invitación al equipo de " + tenant.getName(),
                    "Has sido invitado a unirte al restaurante '" + tenant.getName()
                            + "' con el rol " + request.role().name() + ". Puedes aceptar o rechazar esta invitación.",
                    true,
                    NotificationActionStatus.PENDING,
                    savedInvitation.getId(),
                    dedupKey,
                    metadata
            );
        }

        log.info("Invitación {} enviada al usuario {} ({}) para el tenant {} con rol {}",
                savedInvitation.getId(), user.getId(), user.getEmail(), tenantId, request.role());

        return tenantMemberMapper.toInvitationResponse(savedInvitation);
    }

    private User resolveInviterSafely() {
        if (currentUserProvider == null) {
            return null;
        }
        try {
            return currentUserProvider.getCurrentUser();
        } catch (Exception ex) {
            log.debug("No se pudo resolver el usuario invitador actual: {}", ex.getMessage());
            return null;
        }
    }
}
