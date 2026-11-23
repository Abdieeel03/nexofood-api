package lat.nexofood.api.modules.store.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.modules.notification.web.dto.request.InvitationActionType;
import lat.nexofood.api.modules.notification.web.dto.request.NotificationInvitationDecisionRequest;
import lat.nexofood.api.modules.store.application.service.member.TenantInvitationDecisionService;
import lat.nexofood.api.modules.store.domain.TenantInvitationStatus;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantInvitationRepository;
import lat.nexofood.api.modules.store.web.dto.response.TenantMemberResponse;
import lat.nexofood.api.modules.store.web.mapper.TenantMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invitations")
@RequiredArgsConstructor
@Tag(name = "Tenant Invitations", description = "Gestión de invitaciones de staff del usuario autenticado")
public class TenantInvitationController {

    private final TenantInvitationDecisionService tenantInvitationDecisionService;
    private final TenantInvitationRepository tenantInvitationRepository;
    private final TenantMemberMapper tenantMemberMapper;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @Operation(summary = "Listar mis invitaciones pendientes a restaurantes")
    public ResponseEntity<ApiResponse<List<TenantMemberResponse>>> getMyPendingInvitations() {
        UUID userId = currentUserProvider.getCurrentUserId();
        List<TenantMemberResponse> invitations = tenantInvitationRepository
                .findAllByInvitedUserIdAndStatusOrderByCreatedAtDesc(userId, TenantInvitationStatus.PENDING)
                .stream()
                .map(tenantMemberMapper::toInvitationResponse)
                .toList();
        return ResponseEntity.ok(
                ApiResponse.<List<TenantMemberResponse>>builder()
                        .success(true)
                        .message("Invitaciones obtenidas exitosamente")
                        .data(invitations)
                        .build()
        );
    }

    @RequestMapping(value = "/{invitationId}/respond", method = {RequestMethod.POST, RequestMethod.PATCH})
    @Operation(summary = "Aceptar o rechazar una invitación (ACCEPT o REJECT)")
    public ResponseEntity<ApiResponse<TenantMemberResponse>> respondToInvitation(
            @PathVariable UUID invitationId,
            @Valid @RequestBody NotificationInvitationDecisionRequest request
    ) {
        UUID userId = currentUserProvider.getCurrentUserId();
        TenantMemberResponse response = tenantInvitationDecisionService.respondAndMap(
                invitationId, userId, request.decision());
        String message = request.decision() == InvitationActionType.ACCEPT
                ? "Invitación aceptada exitosamente"
                : "Invitación rechazada exitosamente";
        return ResponseEntity.ok(
                ApiResponse.<TenantMemberResponse>builder()
                        .success(true)
                        .message(message)
                        .data(response)
                        .build()
        );
    }
}
