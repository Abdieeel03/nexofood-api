package lat.nexofood.api.modules.store.application.service.member;

import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.store.application.usecase.member.GetTenantMembersUseCase;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantMemberRepository;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.store.web.dto.response.TenantMemberResponse;
import lat.nexofood.api.modules.store.web.mapper.TenantMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetTenantMembersService implements GetTenantMembersUseCase {

    private final TenantRepository tenantRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final TenantMemberMapper tenantMemberMapper;

    @Override
    @Transactional(readOnly = true)
    public List<TenantMemberResponse> execute(UUID tenantId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado"));
        return tenantMemberRepository.findAllByTenantId(tenantId)
                .stream()
                .map(tenantMemberMapper::toResponse)
                .collect(Collectors.toList());
    }
}
