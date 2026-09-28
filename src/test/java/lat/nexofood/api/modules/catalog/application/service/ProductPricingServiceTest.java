package lat.nexofood.api.modules.catalog.application.service;

import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.domain.ProductPrice;
import lat.nexofood.api.modules.catalog.web.dto.request.ProductPriceRequest;
import lat.nexofood.api.modules.catalog.web.dto.request.ProductRequest;
import lat.nexofood.api.modules.catalog.web.dto.response.ProductResponse;
import lat.nexofood.api.modules.catalog.web.mapper.ProductMapper;
import lat.nexofood.api.modules.catalog.web.mapper.ProductPriceMapper;
import lat.nexofood.api.modules.tenant.domain.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProductPricingServiceTest {

    private ProductPricingService pricingService;

    @BeforeEach
    void setUp() {
        GetBasePriceService basePriceService = new GetBasePriceService();
        GetEffectivePriceRuleService effectiveRuleService = new GetEffectivePriceRuleService(basePriceService);
        CalculateProductPriceService calculatePriceService = new CalculateProductPriceService(effectiveRuleService);
        pricingService = new ProductPricingServiceImpl(calculatePriceService, basePriceService, effectiveRuleService);
    }

    @Test
    @DisplayName("Debe retornar el precio base cuando no hay promociones activas")
    void shouldReturnBasePriceWhenNoPromotions() {
        ProductPrice basePrice = ProductPrice.builder()
                .name("Precio Base")
                .price(new BigDecimal("10.00"))
                .isBase(true)
                .build();

        Product product = Product.builder()
                .id(UUID.randomUUID())
                .name("Hamburguesa Simple")
                .prices(new ArrayList<>(List.of(basePrice)))
                .build();

        assertEquals(new BigDecimal("10.00"), pricingService.calculateCurrentPrice(product));
        assertEquals(new BigDecimal("10.00"), pricingService.getBasePrice(product));
    }

    @Test
    @DisplayName("Debe aplicar precio promocional de Happy Hour dentro del horario y día específico")
    void shouldApplyHappyHourPrice() {
        ProductPrice basePrice = ProductPrice.builder()
                .name("Precio Regular")
                .price(new BigDecimal("8.00"))
                .isBase(true)
                .priority(0)
                .build();

        ProductPrice happyHour = ProductPrice.builder()
                .name("Happy Hour")
                .price(new BigDecimal("5.00"))
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(20, 0))
                .daysOfWeek("FRIDAY,SATURDAY")
                .priority(10)
                .build();

        Product product = Product.builder()
                .id(UUID.randomUUID())
                .name("Cerveza Artesanal")
                .prices(new ArrayList<>(List.of(basePrice, happyHour)))
                .build();

        // Viernes a las 19:00 -> Debe aplicar Happy Hour ($5.00)
        LocalDateTime fridayEvening = LocalDateTime.of(2026, 10, 2, 19, 0); // 2026-10-02 es viernes
        assertEquals(new BigDecimal("5.00"), pricingService.calculateCurrentPrice(product, fridayEvening));

        // Viernes a las 21:00 -> Fuera de horario, debe volver a base ($8.00)
        LocalDateTime fridayNight = LocalDateTime.of(2026, 10, 2, 21, 0);
        assertEquals(new BigDecimal("8.00"), pricingService.calculateCurrentPrice(product, fridayNight));

        // Jueves a las 19:00 -> Día no incluido, debe ser precio base ($8.00)
        LocalDateTime thursdayEvening = LocalDateTime.of(2026, 10, 1, 19, 0); // 2026-10-01 es jueves
        assertEquals(new BigDecimal("8.00"), pricingService.calculateCurrentPrice(product, thursdayEvening));
    }

    @Test
    @DisplayName("Debe seleccionar la promoción con mayor prioridad si coinciden varias")
    void shouldSelectHigherPriorityPromotion() {
        ProductPrice basePrice = ProductPrice.builder()
                .name("Base")
                .price(new BigDecimal("20.00"))
                .isBase(true)
                .priority(0)
                .build();

        ProductPrice promoOctubre = ProductPrice.builder()
                .name("Mes de Octubre")
                .price(new BigDecimal("16.00"))
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 10, 31))
                .priority(5)
                .build();

        ProductPrice blackFriday = ProductPrice.builder()
                .name("Flash Sale")
                .price(new BigDecimal("12.00"))
                .startDate(LocalDate.of(2026, 10, 10))
                .endDate(LocalDate.of(2026, 10, 12))
                .priority(15)
                .build();

        Product product = Product.builder()
                .id(UUID.randomUUID())
                .name("Pizza Especial")
                .prices(new ArrayList<>(List.of(basePrice, promoOctubre, blackFriday)))
                .build();

        // Día del Flash Sale (11 de Octubre) -> Gana Flash Sale por mayor prioridad ($12.00)
        LocalDateTime flashSaleTime = LocalDateTime.of(2026, 10, 11, 12, 0);
        assertEquals(new BigDecimal("12.00"), pricingService.calculateCurrentPrice(product, flashSaleTime));

        // Día normal de Octubre (20 de Octubre) -> Aplica Mes de Octubre ($16.00)
        LocalDateTime octoberTime = LocalDateTime.of(2026, 10, 20, 12, 0);
        assertEquals(new BigDecimal("16.00"), pricingService.calculateCurrentPrice(product, octoberTime));

        // Noviembre -> Vence promo, vuelve a base ($20.00)
        LocalDateTime novemberTime = LocalDateTime.of(2026, 11, 1, 12, 0);
        assertEquals(new BigDecimal("20.00"), pricingService.calculateCurrentPrice(product, novemberTime));
    }

    @Test
    @DisplayName("Debe evaluar correctamente horarios nocturnos que cruzan la medianoche")
    void shouldHandleOvernightSchedule() {
        ProductPrice nightPrice = ProductPrice.builder()
                .name("Late Night")
                .price(new BigDecimal("7.00"))
                .startTime(LocalTime.of(22, 0))
                .endTime(LocalTime.of(3, 0))
                .build();

        // 23:30 -> dentro de rango
        assertTrue(pricingService.isPriceApplicable(nightPrice, LocalDateTime.of(2026, 10, 2, 23, 30)));
        // 01:30 -> dentro de rango
        assertTrue(pricingService.isPriceApplicable(nightPrice, LocalDateTime.of(2026, 10, 3, 1, 30)));
        // 14:00 -> fuera de rango
        assertFalse(pricingService.isPriceApplicable(nightPrice, LocalDateTime.of(2026, 10, 3, 14, 0)));
    }

    @Test
    @DisplayName("Debe mapear Product y ProductPrice hacia DTO y entidad correctamente con el mapper")
    void shouldMapProductWithPricesCorrectly() {
        ProductPriceMapper priceMapper = new ProductPriceMapper();
        ProductMapper productMapper = new ProductMapper(priceMapper, pricingService);

        Tenant tenant = Tenant.builder().id(UUID.randomUUID()).name("Mi Restaurante").build();

        ProductPriceRequest promoRequest = ProductPriceRequest.builder()
                .name("Promo Almuerzo")
                .price(new BigDecimal("12.50"))
                .isBase(false)
                .discountPercentage(new BigDecimal("15.00"))
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(15, 0))
                .priority(1)
                .build();

        ProductRequest request = ProductRequest.builder()
                .name("Lomo Saltado")
                .description("Plato criollo")
                .price(new BigDecimal("18.00"))
                .prices(List.of(promoRequest))
                .isAvailable(true)
                .build();

        Product entity = productMapper.toEntity(request, tenant, null);
        assertNotNull(entity);
        assertEquals(2, entity.getPrices().size());
        assertEquals(new BigDecimal("18.00"), pricingService.getBasePrice(entity));

        ProductResponse response = productMapper.toResponse(entity);
        assertNotNull(response);
        assertEquals("Lomo Saltado", response.name());
        assertEquals(new BigDecimal("18.00"), response.basePrice());
        assertEquals(2, response.prices().size());
    }
}
