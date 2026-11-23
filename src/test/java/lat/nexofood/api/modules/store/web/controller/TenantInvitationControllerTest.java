package lat.nexofood.api.modules.store.web.controller;

import lat.nexofood.api.common.exception.GlobalExceptionHandler;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.modules.notification.web.dto.request.InvitationActionType;
import lat.nexofood.api.modules.store.application.service.member.TenantInvitationDecisionService;
import lat.nexofood.api.modules.store.domain.TenantInvitationStatus;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantInvitationRepository;
import lat.nexofood.api.modules.store.web.dto.response.TenantMemberResponse;
import lat.nexofood.api.modules.store.web.mapper.TenantMemberMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TenantInvitationControllerTest {

    private TenantInvitationDecisionService decisionService;
    private TenantInvitationRepository invitationRepository;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        decisionService = mock(TenantInvitationDecisionService.class);
        invitationRepository = mock(TenantInvitationRepository.class);
        currentUserProvider = mock(CurrentUserProvider.class);

        TenantInvitationController controller = new TenantInvitationController(
                decisionService,
                invitationRepository,
                new TenantMemberMapper(),
                currentUserProvider
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/invitations/{id}/respond acepta ACCEPT y rechaza opciones inválidas con 400")
    void respondAcceptsOnlyAcceptAndReject() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
        when(decisionService.respondAndMap(invitationId, userId, InvitationActionType.ACCEPT)).thenReturn(
                TenantMemberResponse.builder()
                        .id(invitationId)
                        .tenantId(tenantId)
                        .userId(userId)
                        .userEmail("staff@gmail.com")
                        .role(TenantStaffRole.COCINERO)
                        .isActive(true)
                        .invitationStatus(TenantInvitationStatus.ACCEPTED)
                        .build()
        );

        mockMvc.perform(post("/api/v1/invitations/" + invitationId + "/respond")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"ACCEPT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.invitationStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.isActive").value(true));

        mockMvc.perform(post("/api/v1/invitations/" + invitationId + "/respond")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"MAYBE\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(invitationRepository);
    }

    @Test
    @DisplayName("GET /api/v1/invitations lista las invitaciones pendientes del usuario autenticado")
    void getMyPendingInvitationsReturnsList() throws Exception {
        UUID userId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
        when(invitationRepository.findAllByInvitedUserIdAndStatusOrderByCreatedAtDesc(
                userId, TenantInvitationStatus.PENDING)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/invitations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }
}
