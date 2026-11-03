package lat.nexofood.api.modules.catalog.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.domain.ProductPrice;
import lat.nexofood.api.modules.catalog.infrastructure.repository.ProductPriceRepository;
import lat.nexofood.api.modules.catalog.infrastructure.repository.ProductRepository;
import lat.nexofood.api.modules.catalog.web.dto.request.ProductPriceRequest;
import lat.nexofood.api.modules.catalog.web.dto.response.ProductPriceResponse;
import lat.nexofood.api.modules.catalog.web.mapper.ProductPriceMapper;
import lat.nexofood.api.modules.store.domain.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductPriceServiceTest {

    @Mock
    private ProductPriceRepository productPriceRepository;
    @Mock
    private ProductRepository productRepository;

    private ProductPriceServiceImpl productPriceService;
    private UUID tenantId;
    private UUID productId;
    private Product product;
    private ProductPrice basePrice;

    @BeforeEach
    void setUp() {
        productPriceService = new ProductPriceServiceImpl(
                productPriceRepository,
                productRepository,
                new ProductPriceMapper()
        );

        tenantId = UUID.randomUUID();
        productId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().id(tenantId).name("Burger Bros").build();

        basePrice = ProductPrice.builder()
                .id(UUID.randomUUID())
                .name("Precio Base")
                .price(new BigDecimal("25.00"))
                .isBase(true)
                .priority(0)
                .isActive(true)
                .build();

        product = Product.builder()
                .id(productId)
                .tenant(tenant)
                .name("Hamburguesa Doble")
                .prices(new ArrayList<>(List.of(basePrice)))
                .build();
        basePrice.setProduct(product);
    }

    @Test
    @DisplayName("create: agrega una regla promocional válida sin solapamiento ambiguo")
    void createPromotionalPriceSuccessfully() {
        when(productRepository.findByIdAndTenantId(productId, tenantId)).thenReturn(Optional.of(product));
        when(productPriceRepository.findAllByProductId(productId)).thenReturn(List.of(basePrice));
        when(productPriceRepository.save(any(ProductPrice.class))).thenAnswer(inv -> {
            ProductPrice saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        ProductPriceRequest request = ProductPriceRequest.builder()
                .name("Happy Hour Viernes")
                .price(new BigDecimal("19.90"))
                .isBase(false)
                .discountPercentage(new BigDecimal("20.40"))
                .daysOfWeek(new Short[]{5})
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(21, 0))
                .priority(10)
                .isActive(true)
                .build();

        ProductPriceResponse response = productPriceService.create(tenantId, productId, request);

        assertNotNull(response.id());
        assertEquals("Happy Hour Viernes", response.name());
        assertEquals(new BigDecimal("19.90"), response.price());
        assertFalse(response.isBase());
    }

    @Test
    @DisplayName("create: rechaza un segundo precio base activo con 409 Conflict")
    void createRejectsSecondActiveBasePrice() {
        when(productRepository.findByIdAndTenantId(productId, tenantId)).thenReturn(Optional.of(product));
        when(productPriceRepository.findAllByProductId(productId)).thenReturn(List.of(basePrice));

        ProductPriceRequest secondBase = ProductPriceRequest.builder()
                .name("Nuevo Base Duplicado")
                .price(new BigDecimal("30.00"))
                .isBase(true)
                .isActive(true)
                .build();

        assertThrows(ResourceConflictException.class, () ->
                productPriceService.create(tenantId, productId, secondBase)
        );
    }

    @Test
    @DisplayName("create: rechaza rangos de fecha incoherentes o días de semana fuera de ISO 1..7")
    void createRejectsInvalidDateRangeOrDaysOfWeek() {
        when(productRepository.findByIdAndTenantId(productId, tenantId)).thenReturn(Optional.of(product));

        ProductPriceRequest invalidDates = ProductPriceRequest.builder()
                .name("Fechas Invertidas")
                .price(new BigDecimal("15.00"))
                .startDate(LocalDate.of(2026, 10, 20))
                .endDate(LocalDate.of(2026, 10, 10))
                .build();

        assertThrows(BadRequestException.class, () ->
                productPriceService.create(tenantId, productId, invalidDates)
        );

        ProductPriceRequest invalidDays = ProductPriceRequest.builder()
                .name("Día Inválido")
                .price(new BigDecimal("15.00"))
                .daysOfWeek(new Short[]{0, 8})
                .build();

        assertThrows(BadRequestException.class, () ->
                productPriceService.create(tenantId, productId, invalidDays)
        );
    }

    @Test
    @DisplayName("create: rechaza solapamiento ambiguo de igual prioridad y permite solapamiento con prioridad distinta")
    void createDetectsAmbiguousOverlapWithSamePriority() {
        ProductPrice existingPromo = ProductPrice.builder()
                .id(UUID.randomUUID())
                .product(product)
                .name("Promo Fin de Semana")
                .price(new BigDecimal("20.00"))
                .isBase(false)
                .daysOfWeek(new Short[]{5, 6})
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(22, 0))
                .priority(10)
                .isActive(true)
                .build();

        when(productRepository.findByIdAndTenantId(productId, tenantId)).thenReturn(Optional.of(product));
        when(productPriceRepository.findAllByProductId(productId)).thenReturn(List.of(basePrice, existingPromo));

        // Misma prioridad (10), Viernes (5), 19:00 a 21:00 -> Solapamiento ambiguo
        ProductPriceRequest ambiguousRequest = ProductPriceRequest.builder()
                .name("Promo Viernes Noche")
                .price(new BigDecimal("18.00"))
                .isBase(false)
                .daysOfWeek(new Short[]{5})
                .startTime(LocalTime.of(19, 0))
                .endTime(LocalTime.of(21, 0))
                .priority(10)
                .isActive(true)
                .build();

        assertThrows(ResourceConflictException.class, () ->
                productPriceService.create(tenantId, productId, ambiguousRequest)
        );

        // Prioridad distinta (20) -> Desempate determinista permitido
        ProductPriceRequest higherPriorityRequest = ProductPriceRequest.builder()
                .name("Flash Viernes Noche")
                .price(new BigDecimal("18.00"))
                .isBase(false)
                .daysOfWeek(new Short[]{5})
                .startTime(LocalTime.of(19, 0))
                .endTime(LocalTime.of(21, 0))
                .priority(20)
                .isActive(true)
                .build();

        when(productPriceRepository.save(any(ProductPrice.class))).thenAnswer(inv -> inv.getArgument(0));
        ProductPriceResponse created = productPriceService.create(tenantId, productId, higherPriorityRequest);
        assertEquals(20, created.priority());
    }

    @Test
    @DisplayName("delete y update: impiden eliminar o desactivar el único precio base activo")
    void preventsDeletingOrDeactivatingOnlyBasePrice() {
        when(productRepository.findByIdAndTenantId(productId, tenantId)).thenReturn(Optional.of(product));
        when(productPriceRepository.findByIdAndProductId(basePrice.getId(), productId)).thenReturn(Optional.of(basePrice));

        assertThrows(BadRequestException.class, () ->
                productPriceService.delete(tenantId, productId, basePrice.getId())
        );

        when(productPriceRepository.findAllByProductId(productId)).thenReturn(List.of(basePrice));
        ProductPriceRequest deactivateBaseRequest = ProductPriceRequest.builder()
                .name("Precio Base")
                .price(new BigDecimal("25.00"))
                .isBase(true)
                .isActive(false)
                .build();

        assertThrows(BadRequestException.class, () ->
                productPriceService.update(tenantId, productId, basePrice.getId(), deactivateBaseRequest)
        );
    }
}
