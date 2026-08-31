package lat.nexofood.api.modules.auth.mapper;

import lat.nexofood.api.common.util.GeoUtils;
import lat.nexofood.api.modules.auth.domain.CustomerAddress;
import lat.nexofood.api.modules.auth.domain.User;
import lat.nexofood.api.modules.auth.dto.request.CustomerAddressRequest;
import lat.nexofood.api.modules.auth.dto.response.CustomerAddressResponse;
import org.springframework.stereotype.Component;

@Component
public class CustomerAddressMapper {

    public CustomerAddressResponse toResponse(CustomerAddress address) {
        if (address == null) {
            return null;
        }
        return CustomerAddressResponse.builder()
                .id(address.getId())
                .userId(address.getUser() != null ? address.getUser().getId() : null)
                .title(address.getTitle())
                .addressLine(address.getAddressLine())
                .reference(address.getReference())
                .latitude(GeoUtils.getLatitude(address.getLocation()))
                .longitude(GeoUtils.getLongitude(address.getLocation()))
                .createdAt(address.getCreatedAt())
                .updatedAt(address.getUpdatedAt())
                .build();
    }

    public CustomerAddress toEntity(CustomerAddressRequest request, User user) {
        if (request == null) {
            return null;
        }
        return CustomerAddress.builder()
                .user(user)
                .title(request.title() != null ? request.title() : "Casa")
                .addressLine(request.addressLine())
                .reference(request.reference())
                .location(GeoUtils.createPoint(request.latitude(), request.longitude()))
                .build();
    }
}
