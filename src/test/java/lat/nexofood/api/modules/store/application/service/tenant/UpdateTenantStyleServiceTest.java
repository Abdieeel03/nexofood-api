package lat.nexofood.api.modules.store.application.service.tenant;

import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.store.domain.StorefrontStyle;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.store.web.dto.response.TenantResponse;
import lat.nexofood.api.modules.store.web.mapper.TenantMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateTenantStyleServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Spy
    private TenantMapper tenantMapper = new TenantMapper();

    @InjectMocks
    private UpdateTenantStyleService updateTenantStyleService;

    @Test
    @DisplayName("Debe actualizar el estilo del storefront del tenant y retornarlo en TenantResponse")
    void shouldUpdateStorefrontStyle() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .name("Nexo Burger")
                .slug("nexo-burger")
                .storefrontStyle(StorefrontStyle.MODERN)
                .isActive(true)
                .isOpen(true)
                .build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        TenantResponse response = updateTenantStyleService.execute(tenantId, StorefrontStyle.MINIMALIST);

        assertThat(response.storefrontStyle()).isEqualTo(StorefrontStyle.MINIMALIST);
        assertThat(tenant.getStorefrontStyle()).isEqualTo(StorefrontStyle.MINIMALIST);
        verify(tenantRepository).save(tenant);
    }

    @Test
    @DisplayName("Debe usar MODERN como fallback cuando se envía estilo nulo")
    void shouldFallbackToModernWhenNullStyle() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .name("Nexo Burger")
                .slug("nexo-burger")
                .storefrontStyle(StorefrontStyle.ENTERPRISE)
                .build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        TenantResponse response = updateTenantStyleService.execute(tenantId, null);

        assertThat(response.storefrontStyle()).isEqualTo(StorefrontStyle.MODERN);
    }

    @Test
    @DisplayName("Debe lanzar ResourceNotFoundException si el tenant no existe")
    void shouldThrowWhenTenantNotFound() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateTenantStyleService.execute(tenantId, StorefrontStyle.CLASSIC))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
