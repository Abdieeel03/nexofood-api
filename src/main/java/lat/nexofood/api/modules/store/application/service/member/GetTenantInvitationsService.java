package lat.nexofood.api.modules.store.application.service.member;

import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.store.application.usecase.member.GetTenantInvitationsUseCase;
import lat.nexofood.api.modules.store.domain.TenantInvitation;
import lat.nexofood.api.modules.store.domain.TenantInvitationStatus;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantInvitationRepository;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.store.web.dto.response.TenantMemberResponse;
import lat.nexofood.api.modules.store.web.mapper.TenantMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetTenantInvitationsService implements GetTenantInvitationsUseCase {

    private final TenantRepository tenantRepository;
    private final TenantInvitationRepository tenantInvitationRepository;
    private final TenantMemberMapper tenantMemberMapper;

    @Override
    @Transactional(readOnly = true)
    public List<TenantMemberResponse> execute(UUID tenantId, TenantInvitationStatus status) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado"));

        List<TenantInvitation> invitations = status != null
                ? tenantInvitationRepository.findAllByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status)
                : tenantInvitationRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId);

        return invitations.stream()
                .map(tenantMemberMapper::toInvitationResponse)
                .toList();
    }
}
