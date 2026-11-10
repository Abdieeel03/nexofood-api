package lat.nexofood.api.modules.cart.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.cart.domain.Cart;
import lat.nexofood.api.modules.cart.domain.CartItem;
import lat.nexofood.api.modules.cart.infrastructure.repository.CartItemRepository;
import lat.nexofood.api.modules.cart.infrastructure.repository.CartRepository;
import lat.nexofood.api.modules.cart.web.dto.request.CartItemRequest;
import lat.nexofood.api.modules.cart.web.dto.request.CartItemUpdateRequest;
import lat.nexofood.api.modules.cart.web.dto.request.CartNotesRequest;
import lat.nexofood.api.modules.cart.web.dto.response.CartResponse;
import lat.nexofood.api.modules.cart.web.mapper.CartItemMapper;
import lat.nexofood.api.modules.cart.web.mapper.CartMapper;
import lat.nexofood.api.modules.catalog.application.service.CalculateProductPriceService;
import lat.nexofood.api.modules.catalog.application.service.GetBasePriceService;
import lat.nexofood.api.modules.catalog.application.service.GetEffectivePriceRuleService;
import lat.nexofood.api.modules.catalog.application.service.ProductPricingService;
import lat.nexofood.api.modules.catalog.application.service.ProductPricingServiceImpl;
import lat.nexofood.api.modules.catalog.domain.Category;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.domain.ProductPrice;
import lat.nexofood.api.modules.catalog.infrastructure.repository.ProductRepository;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.store.application.service.customer.CurrentCustomerResolver;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import lat.nexofood.api.modules.store.domain.TenantCustomerId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private CurrentCustomerResolver currentCustomerResolver;

    private ProductPricingService productPricingService;
    private CartItemService cartItemService;
    private CartServiceImpl cartService;

    private Tenant tenant;
    private User user;
    private TenantCustomer customer;
    private Product burger;

    @BeforeEach
    void setUp() {
        GetBasePriceService basePriceService = new GetBasePriceService();
        GetEffectivePriceRuleService effectiveRuleService = new GetEffectivePriceRuleService(basePriceService);
        CalculateProductPriceService calculatePriceService = new CalculateProductPriceService(effectiveRuleService);
        productPricingService = new ProductPricingServiceImpl(
                calculatePriceService, basePriceService, effectiveRuleService
        );

        CartItemMapper cartItemMapper = new CartItemMapper(productPricingService);
        CartMapper cartMapper = new CartMapper(cartItemMapper);

        cartItemService = new CartItemServiceImpl(cartItemRepository, productRepository, productPricingService);
        cartService = new CartServiceImpl(
                cartRepository,
                cartItemRepository,
                cartItemService,
                currentCustomerResolver,
                productPricingService,
                cartMapper,
                cartItemMapper
        );

        tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .name("Burger Bros")
                .slug("burger-bros")
                .isActive(true)
                .build();

        user = User.builder()
                .id(UUID.randomUUID())
                .email("cliente@nexofood.lat")
                .fullName("Lucía Cliente")
                .isActive(true)
                .build();

        customer = TenantCustomer.builder()
                .id(new TenantCustomerId(tenant.getId(), user.getId()))
                .tenant(tenant)
                .user(user)
                .isBlocked(false)
                .build();

        Category activeCategory = Category.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .name("Hamburguesas")
                .isActive(true)
                .build();

        ProductPrice basePrice = ProductPrice.builder()
                .id(UUID.randomUUID())
                .name("Precio Base")
                .price(new BigDecimal("25.00"))
                .isBase(true)
                .isActive(true)
                .priority(0)
                .build();

        burger = Product.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .category(activeCategory)
                .name("Burger Royal")
                .isAvailable(true)
                .prices(new ArrayList<>(List.of(basePrice)))
                .build();
    }

    @Test
    @DisplayName("addItem: crea carrito único para el cliente en la tienda y congela unitPrice desde ProductPricingService")
    void addItemCreatesSingleCartPerStoreAndFreezesBackendPrice() {
        when(currentCustomerResolver.resolveBySlug("burger-bros")).thenReturn(customer);
        when(cartRepository.findByTenantIdAndCustomerUserId(tenant.getId(), user.getId()))
                .thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> {
            Cart c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(UUID.randomUUID());
            }
            return c;
        });
        when(productRepository.findByIdAndTenantId(burger.getId(), tenant.getId()))
                .thenReturn(Optional.of(burger));
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> {
            CartItem item = inv.getArgument(0);
            if (item.getId() == null) {
                item.setId(UUID.randomUUID());
            }
            return item;
        });

        CartItemRequest request = CartItemRequest.builder()
                .productId(burger.getId())
                .quantity(2)
                .notes("Sin cebolla")
                .build();

        CartResponse response = cartService.addItem("burger-bros", request);

        assertEquals(tenant.getId(), response.tenantId());
        assertEquals(user.getId(), response.customerId());
        assertEquals(1, response.items().size());
        assertEquals(new BigDecimal("25.00"), response.items().getFirst().unitPrice());
        assertEquals(new BigDecimal("50.00"), response.items().getFirst().subtotal());
        assertEquals(new BigDecimal("50.00"), response.total());
    }

    @Test
    @DisplayName("addItem: impide agregar un producto que pertenece a otra tienda (aislamiento multi-tenant)")
    void addItemRejectsProductFromAnotherStore() {
        UUID foreignProductId = UUID.randomUUID();
        Cart existingCart = Cart.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .customer(customer)
                .build();

        when(currentCustomerResolver.resolveBySlug("burger-bros")).thenReturn(customer);
        when(cartRepository.findByTenantIdAndCustomerUserId(tenant.getId(), user.getId()))
                .thenReturn(Optional.of(existingCart));
        when(productRepository.findByIdAndTenantId(foreignProductId, tenant.getId()))
                .thenReturn(Optional.empty());

        CartItemRequest request = CartItemRequest.builder()
                .productId(foreignProductId)
                .quantity(1)
                .build();

        assertThrows(ResourceNotFoundException.class, () ->
                cartService.addItem("burger-bros", request)
        );
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("addItem: rechaza producto agotado (isAvailable=false) o con categoría inactiva")
    void addItemRejectsUnavailableProduct() {
        burger.setIsAvailable(false);
        Cart existingCart = Cart.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .customer(customer)
                .build();

        when(currentCustomerResolver.resolveBySlug("burger-bros")).thenReturn(customer);
        when(cartRepository.findByTenantIdAndCustomerUserId(tenant.getId(), user.getId()))
                .thenReturn(Optional.of(existingCart));
        when(productRepository.findByIdAndTenantId(burger.getId(), tenant.getId()))
                .thenReturn(Optional.of(burger));

        CartItemRequest request = CartItemRequest.builder()
                .productId(burger.getId())
                .quantity(1)
                .build();

        assertThrows(BadRequestException.class, () ->
                cartService.addItem("burger-bros", request)
        );
    }

    @Test
    @DisplayName("getMyCart: recalcula precios dinámicos vigentes al leer y reporta cambios o productos agotados")
    void getMyCartRecalculatesDynamicPricesAndReportsWarnings() {
        // Ítem guardado anteriormente a 25.00, pero el precio base actual bajó a 20.00
        burger.getPrices().getFirst().setPrice(new BigDecimal("20.00"));

        CartItem item = CartItem.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .product(burger)
                .quantity(2)
                .unitPrice(new BigDecimal("25.00"))
                .subtotal(new BigDecimal("50.00"))
                .build();

        Cart existingCart = Cart.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .customer(customer)
                .items(new ArrayList<>(List.of(item)))
                .total(new BigDecimal("50.00"))
                .build();
        item.setCart(existingCart);

        when(currentCustomerResolver.resolveBySlug("burger-bros")).thenReturn(customer);
        when(cartRepository.findByTenantIdAndCustomerUserId(tenant.getId(), user.getId()))
                .thenReturn(Optional.of(existingCart));
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> inv.getArgument(0));

        CartResponse response = cartService.getMyCart("burger-bros");

        assertTrue(response.hasPriceChanges());
        assertFalse(response.warnings().isEmpty());
        assertEquals(new BigDecimal("20.00"), response.items().getFirst().unitPrice());
        assertEquals(new BigDecimal("25.00"), response.items().getFirst().previousUnitPrice());
        assertTrue(response.items().getFirst().priceChanged());
        assertEquals(new BigDecimal("40.00"), response.total());
    }

    @Test
    @DisplayName("updateItem, updateCartNotes, removeItem y clearCart: actualizan y vacían el carrito correctamente")
    void updateRemoveAndClearCartOperationsWorkCorrectly() {
        UUID itemId = UUID.randomUUID();
        CartItem item = CartItem.builder()
                .id(itemId)
                .tenant(tenant)
                .product(burger)
                .quantity(1)
                .unitPrice(new BigDecimal("25.00"))
                .subtotal(new BigDecimal("25.00"))
                .build();

        Cart existingCart = Cart.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .customer(customer)
                .items(new ArrayList<>(List.of(item)))
                .total(new BigDecimal("25.00"))
                .build();
        item.setCart(existingCart);

        when(currentCustomerResolver.resolveBySlug("burger-bros")).thenReturn(customer);
        when(cartRepository.findByTenantIdAndCustomerUserId(tenant.getId(), user.getId()))
                .thenReturn(Optional.of(existingCart));
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. Actualizar cantidad y notas del ítem
        CartResponse updated = cartService.updateItem(
                "burger-bros",
                itemId,
                CartItemUpdateRequest.builder().quantity(3).notes("Extra queso").build()
        );
        assertEquals(3, updated.items().getFirst().quantity());
        assertEquals("Extra queso", updated.items().getFirst().notes());
        assertEquals(new BigDecimal("75.00"), updated.total());

        // 2. Actualizar notas generales del carrito
        CartResponse withNotes = cartService.updateCartNotes(
                "burger-bros",
                CartNotesRequest.builder().notes("Tocar el timbre 2B").build()
        );
        assertEquals("Tocar el timbre 2B", withNotes.notes());

        // 3. Vaciar carrito
        CartResponse cleared = cartService.clearCart("burger-bros");
        assertTrue(cleared.items().isEmpty());
        assertEquals(new BigDecimal("0.00"), cleared.total());
        verify(cartItemRepository).deleteAllByCartId(existingCart.getId());
    }
}
