package lat.nexofood.api.modules.catalog.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.catalog.domain.Tax;
import lat.nexofood.api.modules.catalog.infrastructure.repository.ProductRepository;
import lat.nexofood.api.modules.catalog.infrastructure.repository.TaxRepository;
import lat.nexofood.api.modules.catalog.web.dto.request.TaxRequest;
import lat.nexofood.api.modules.catalog.web.dto.response.TaxResponse;
import lat.nexofood.api.modules.catalog.web.mapper.TaxMapper;
import lat.nexofood.api.modules.order.infrastructure.repository.OrderItemRepository;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaxServiceTest {

    @Mock
    private TaxRepository taxRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private OrderItemRepository orderItemRepository;

    private TaxServiceImpl taxService;
    private Tenant tenant;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        taxService = new TaxServiceImpl(
                taxRepository,
                tenantRepository,
                productRepository,
                orderItemRepository,
                new TaxMapper()
        );
        tenantId = UUID.randomUUID();
        tenant = Tenant.builder().id(tenantId).name("Restaurante Test").build();
    }

    @Test
    @DisplayName("create: crea un impuesto exitosamente para el restaurante")
    void createTaxSuccessfully() {
        TaxRequest request = TaxRequest.builder()
                .name("IGV 18%")
                .rate(new BigDecimal("18.00"))
                .code("1000")
                .isInclusive(true)
                .isActive(true)
                .build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(taxRepository.save(any(Tax.class))).thenAnswer(inv -> {
            Tax t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        TaxResponse response = taxService.create(tenantId, request);

        assertNotNull(response.id());
        assertEquals(tenantId, response.tenantId());
        assertEquals("IGV 18%", response.name());
        assertEquals(new BigDecimal("18.00"), response.rate());
        assertTrue(response.isInclusive());
        assertTrue(response.isActive());
    }

    @Test
    @DisplayName("findAllByTenant y findActiveByTenant: listan impuestos correctamente")
    void listTaxesByTenant() {
        Tax activeTax = Tax.builder().id(UUID.randomUUID()).tenant(tenant).name("IGV").rate(new BigDecimal("18.00")).isActive(true).build();
        Tax inactiveTax = Tax.builder().id(UUID.randomUUID()).tenant(tenant).name("Antiguo").rate(new BigDecimal("10.00")).isActive(false).build();

        when(taxRepository.findAllByTenantId(tenantId)).thenReturn(List.of(activeTax, inactiveTax));
        when(taxRepository.findAllByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(activeTax));

        assertEquals(2, taxService.findAllByTenant(tenantId).size());
        assertEquals(1, taxService.findActiveByTenant(tenantId).size());
    }

    @Test
    @DisplayName("update: permite cambiar la tasa si el impuesto no está en uso")
    void updateTaxRateWhenUnused() {
        UUID taxId = UUID.randomUUID();
        Tax tax = Tax.builder()
                .id(taxId)
                .tenant(tenant)
                .name("ICBP")
                .rate(new BigDecimal("5.00"))
                .isInclusive(false)
                .isActive(true)
                .build();

        when(taxRepository.findByIdAndTenantId(taxId, tenantId)).thenReturn(Optional.of(tax));
        when(productRepository.existsByTaxId(taxId)).thenReturn(false);
        when(orderItemRepository.existsByTaxId(taxId)).thenReturn(false);
        when(taxRepository.save(any(Tax.class))).thenAnswer(inv -> inv.getArgument(0));

        TaxRequest request = TaxRequest.builder()
                .name("ICBP Actualizado")
                .rate(new BigDecimal("8.00"))
                .code("9999")
                .isInclusive(true)
                .isActive(true)
                .build();

        TaxResponse updated = taxService.update(tenantId, taxId, request);
        assertEquals("ICBP Actualizado", updated.name());
        assertEquals(new BigDecimal("8.00"), updated.rate());
        assertTrue(updated.isInclusive());
    }

    @Test
    @DisplayName("toggleStatus: activa o desactiva un impuesto")
    void toggleTaxStatus() {
        UUID taxId = UUID.randomUUID();
        Tax tax = Tax.builder().id(taxId).tenant(tenant).name("IGV").rate(new BigDecimal("18.00")).isActive(true).build();

        when(taxRepository.findByIdAndTenantId(taxId, tenantId)).thenReturn(Optional.of(tax));
        when(taxRepository.save(any(Tax.class))).thenAnswer(inv -> inv.getArgument(0));

        TaxResponse response = taxService.toggleStatus(tenantId, taxId, false);
        assertFalse(response.isActive());
    }

    @Test
    @DisplayName("delete: rechaza eliminar un impuesto asignado a productos o ventas históricas")
    void deleteTaxRejectedWhenInUse() {
        UUID taxId = UUID.randomUUID();
        Tax tax = Tax.builder().id(taxId).tenant(tenant).name("IGV").rate(new BigDecimal("18.00")).build();

        when(taxRepository.findByIdAndTenantId(taxId, tenantId)).thenReturn(Optional.of(tax));
        when(productRepository.existsByTaxId(taxId)).thenReturn(true);

        assertThrows(BadRequestException.class, () -> taxService.delete(tenantId, taxId));
        verify(taxRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete: elimina el impuesto cuando no tiene productos ni ventas asociadas")
    void deleteTaxSuccessfullyWhenUnused() {
        UUID taxId = UUID.randomUUID();
        Tax tax = Tax.builder().id(taxId).tenant(tenant).name("Temporal").rate(new BigDecimal("5.00")).build();

        when(taxRepository.findByIdAndTenantId(taxId, tenantId)).thenReturn(Optional.of(tax));
        when(productRepository.existsByTaxId(taxId)).thenReturn(false);
        when(orderItemRepository.existsByTaxId(taxId)).thenReturn(false);

        taxService.delete(tenantId, taxId);
        verify(taxRepository).delete(tax);
    }

    @Test
    @DisplayName("findByIdAndTenant: lanza ResourceNotFoundException si el impuesto no pertenece al tenant")
    void findByIdThrowsWhenNotFound() {
        UUID taxId = UUID.randomUUID();
        when(taxRepository.findByIdAndTenantId(taxId, tenantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> taxService.findByIdAndTenant(taxId, tenantId));
    }
}
