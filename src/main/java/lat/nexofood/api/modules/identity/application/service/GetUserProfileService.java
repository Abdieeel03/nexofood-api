package lat.nexofood.api.modules.identity.application.service;

import lat.nexofood.api.common.constants.ErrorMessages;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.identity.application.usecase.GetUserProfileUseCase;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.infrastructure.repository.CustomerAddressRepository;
import lat.nexofood.api.modules.identity.infrastructure.repository.UserRepository;
import lat.nexofood.api.modules.identity.web.dto.response.CustomerAddressResponse;
import lat.nexofood.api.modules.identity.web.dto.response.TenantStaffMembershipDto;
import lat.nexofood.api.modules.identity.web.dto.response.UserProfileResponse;
import lat.nexofood.api.modules.identity.web.mapper.CustomerAddressMapper;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetUserProfileService implements GetUserProfileUseCase {

    private final UserRepository userRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final CustomerAddressRepository customerAddressRepository;
    private final CustomerAddressMapper customerAddressMapper;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse execute(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.USER_NOT_FOUND));

        List<TenantStaffMembershipDto> memberships = tenantMemberRepository
                .findAllByUserId(user.getId())
                .stream()
                .map(member -> TenantStaffMembershipDto.builder()
                        .tenantId(member.getTenant().getId())
                        .tenantName(member.getTenant().getName())
                        .tenantSlug(member.getTenant().getSlug())
                        .staffRole(member.getRole())
                        .build())
                .collect(Collectors.toList());

        List<CustomerAddressResponse> addresses = customerAddressRepository
                .findAllByUserId(user.getId())
                .stream()
                .map(customerAddressMapper::toResponse)
                .collect(Collectors.toList());

        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .systemRole(user.getSystemRole().name())
                .isActive(user.getIsActive())
                .staffMemberships(memberships)
                .addresses(addresses)
                .build();
    }
}
