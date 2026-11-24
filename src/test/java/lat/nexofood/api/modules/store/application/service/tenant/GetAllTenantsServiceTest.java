package lat.nexofood.api.modules.store.application.service.tenant;

import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.store.web.dto.response.TenantResponse;
import lat.nexofood.api.modules.store.web.mapper.TenantMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAllTenantsServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    private GetAllTenantsService getAllTenantsService;

    @BeforeEach
    void setUp() {
        getAllTenantsService = new GetAllTenantsService(tenantRepository, new TenantMapper());
    }

    @Test
    @DisplayName("Lista tiendas paginadas aplicando Specification y calculando horario de atención")
    @SuppressWarnings("unchecked")
    void returnsPagedTenantsWithComputedOpenStatus() {
        Pageable pageable = PageRequest.of(0, 20);
        Tenant open24h = Tenant.builder()
                .id(UUID.randomUUID())
                .name("Restaurante 24H")
                .slug("restaurante-24h")
                .isActive(true)
                .openingTime(LocalTime.of(0, 0))
                .closingTime(LocalTime.of(0, 0))
                .isOpen(true)
                .build();

        Tenant inactive = Tenant.builder()
                .id(UUID.randomUUID())
                .name("Restaurante Cerrado")
                .slug("restaurante-cerrado")
                .isActive(false)
                .isOpen(true)
                .build();

        when(tenantRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(open24h, inactive), pageable, 2));

        PageResponse<TenantResponse> response = getAllTenantsService.execute(null, null, "restaurante", pageable);

        assertEquals(2, response.totalElements());
        assertTrue(response.content().get(0).isOpen());
        assertFalse(response.content().get(1).isOpen());
        verify(tenantRepository).findAll(any(Specification.class), eq(pageable));
    }
}
