package lat.nexofood.api.modules.store.application.service.member;

import lat.nexofood.api.common.config.TenantConnectionPreparer;
import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ForbiddenException;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.notification.application.service.NotificationInvitationResponder;
import lat.nexofood.api.modules.notification.application.service.NotificationService;
import lat.nexofood.api.modules.notification.domain.NotificationActionStatus;
import lat.nexofood.api.modules.notification.domain.NotificationType;
import lat.nexofood.api.modules.notification.web.dto.request.InvitationActionType;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantInvitation;
import lat.nexofood.api.modules.store.domain.TenantInvitationStatus;
import lat.nexofood.api.modules.store.domain.TenantMember;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantInvitationRepository;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantMemberRepository;
import lat.nexofood.api.modules.store.web.dto.response.TenantMemberResponse;
import lat.nexofood.api.modules.store.web.mapper.TenantMemberMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class TenantInvitationDecisionService implements NotificationInvitationResponder {

    private final TenantInvitationRepository tenantInvitationRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final NotificationService notificationService;
    private final TenantConnectionPreparer tenantConnectionPreparer;
    private final TenantMemberMapper tenantMemberMapper;

    @Autowired
    public TenantInvitationDecisionService(
            TenantInvitationRepository tenantInvitationRepository,
            TenantMemberRepository tenantMemberRepository,
            @Lazy NotificationService notificationService,
            @Autowired(required = false) TenantConnectionPreparer tenantConnectionPreparer,
            @Autowired(required = false) TenantMemberMapper tenantMemberMapper
    ) {
        this.tenantInvitationRepository = tenantInvitationRepository;
        this.tenantMemberRepository = tenantMemberRepository;
        this.notificationService = notificationService;
        this.tenantConnectionPreparer = tenantConnectionPreparer;
        this.tenantMemberMapper = tenantMemberMapper != null ? tenantMemberMapper : new TenantMemberMapper();
    }

    public TenantInvitationDecisionService(
            TenantInvitationRepository tenantInvitationRepository,
            TenantMemberRepository tenantMemberRepository,
            NotificationService notificationService,
            TenantConnectionPreparer tenantConnectionPreparer
    ) {
        this(tenantInvitationRepository, tenantMemberRepository, notificationService, tenantConnectionPreparer, new TenantMemberMapper());
    }

    @Override
    @Transactional
    public void onInvitationDecision(UUID invitationId, UUID recipientUserId, InvitationActionType decision) {
        processDecision(invitationId, recipientUserId, decision);
    }

    @Transactional
    public TenantMemberResponse respondAndMap(UUID invitationId, UUID recipientUserId, InvitationActionType decision) {
        TenantInvitation updatedInvitation = processDecision(invitationId, recipientUserId, decision);
        if (notificationService != null) {
            NotificationActionStatus notificationStatus = decision == InvitationActionType.ACCEPT
                    ? NotificationActionStatus.ACCEPTED
                    : NotificationActionStatus.REJECTED;
            notificationService.syncInvitationNotificationStatus(invitationId, notificationStatus);
        }
        return tenantMemberMapper.toInvitationResponse(updatedInvitation);
    }

    private TenantInvitation processDecision(UUID invitationId, UUID recipientUserId, InvitationActionType decision) {
        if (decision == null) {
            throw new BadRequestException("La decisión (ACCEPT o REJECT) es obligatoria");
        }

        TenantInvitation invitation = tenantInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitación no encontrada"));

        User invitedUser = invitation.getInvitedUser();
        if (invitedUser == null || invitedUser.getId() == null || !invitedUser.getId().equals(recipientUserId)) {
            throw new ForbiddenException("No tienes permiso para responder esta invitación");
        }

        if (invitation.getStatus() != TenantInvitationStatus.PENDING) {
            throw new ResourceConflictException("Esta invitación ya fue respondida");
        }

        Tenant tenant = invitation.getTenant();
        OffsetDateTime now = OffsetDateTime.now();

        if (decision == InvitationActionType.ACCEPT) {
            invitation.setStatus(TenantInvitationStatus.ACCEPTED);
            invitation.setRespondedAt(now);
            TenantInvitation savedInvitation = tenantInvitationRepository.save(invitation);

            if (tenant != null && tenant.getId() != null) {
                if (tenantConnectionPreparer != null) {
                    tenantConnectionPreparer.setTenantContext(tenant.getId());
                }

                if (!tenantMemberRepository.existsByTenantIdAndUserId(tenant.getId(), invitedUser.getId())) {
                    TenantMember newMember = TenantMember.builder()
                            .tenant(tenant)
                            .user(invitedUser)
                            .role(invitation.getRole())
                            .isActive(true)
                            .build();
                    tenantMemberRepository.save(newMember);
                }
            }

            notifyOwnerOfDecision(savedInvitation, true);
            log.info("Invitación {} aceptada por usuario {} para tenant {}",
                    invitationId, recipientUserId, tenant != null ? tenant.getId() : null);
            return savedInvitation;
        }

        invitation.setStatus(TenantInvitationStatus.REJECTED);
        invitation.setRespondedAt(now);
        TenantInvitation savedInvitation = tenantInvitationRepository.save(invitation);

        notifyOwnerOfDecision(savedInvitation, false);
        log.info("Invitación {} rechazada por usuario {} para tenant {}",
                invitationId, recipientUserId, tenant != null ? tenant.getId() : null);
        return savedInvitation;
    }

    private void notifyOwnerOfDecision(TenantInvitation invitation, boolean accepted) {
        if (notificationService == null) {
            return;
        }
        Tenant tenant = invitation.getTenant();
        User owner = tenant != null && tenant.getOwner() != null ? tenant.getOwner() : invitation.getInviter();
        if (owner == null || owner.getId() == null) {
            return;
        }

        User invitedUser = invitation.getInvitedUser();
        String invitedDisplay = invitedUser != null && invitedUser.getFullName() != null
                ? invitedUser.getFullName() + " (" + invitedUser.getEmail() + ")"
                : invitation.getInvitedEmail();

        String storeName = tenant != null ? tenant.getName() : "tu restaurante";
        NotificationType type = accepted ? NotificationType.INVITATION_ACCEPTED : NotificationType.INVITATION_REJECTED;
        String title = accepted
                ? "Invitación aceptada en " + storeName
                : "Invitación rechazada en " + storeName;
        String message = accepted
                ? invitedDisplay + " aceptó la invitación para unirse a '" + storeName + "' como " + invitation.getRole().name() + "."
                : invitedDisplay + " rechazó la invitación para unirse a '" + storeName + "' como " + invitation.getRole().name() + ".";
        String dedupKey = type.name() + ":" + invitation.getId();

        Map<String, Object> metadata = new LinkedHashMap<>();
        if (invitation.getId() != null) {
            metadata.put("invitationId", invitation.getId().toString());
        }
        if (tenant != null && tenant.getId() != null) {
            metadata.put("tenantId", tenant.getId().toString());
        }
        if (invitedUser != null && invitedUser.getId() != null) {
            metadata.put("invitedUserId", invitedUser.getId().toString());
        }
        metadata.put("invitedEmail", invitation.getInvitedEmail());
        metadata.put("role", invitation.getRole().name());
        metadata.put("status", invitation.getStatus().name());

        notificationService.createNotification(
                owner,
                tenant,
                type,
                title,
                message,
                false,
                NotificationActionStatus.NONE,
                invitation.getId(),
                dedupKey,
                metadata
        );
    }
}
