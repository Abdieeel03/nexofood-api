package lat.nexofood.api.modules.order.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.util.GeoUtils;
import lat.nexofood.api.modules.catalog.application.service.ProductPricingService;
import lat.nexofood.api.modules.catalog.application.service.TaxCalculationResult;
import lat.nexofood.api.modules.catalog.application.service.TaxCalculationService;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.infrastructure.repository.ProductRepository;
import lat.nexofood.api.modules.identity.domain.CustomerAddress;
import lat.nexofood.api.modules.identity.infrastructure.repository.CustomerAddressRepository;
import lat.nexofood.api.modules.order.domain.DeliveryType;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.order.domain.OrderItem;
import lat.nexofood.api.modules.order.domain.OrderStatus;
import lat.nexofood.api.modules.order.infrastructure.repository.OrderRepository;
import lat.nexofood.api.modules.order.web.dto.request.OrderCreateRequest;
import lat.nexofood.api.modules.order.web.dto.request.OrderItemRequest;
import lat.nexofood.api.modules.order.web.dto.response.OrderResponse;
import lat.nexofood.api.modules.order.web.mapper.OrderItemMapper;
import lat.nexofood.api.modules.order.web.mapper.OrderMapper;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantCustomerRepository;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final TenantRepository tenantRepository;
    private final TenantCustomerRepository tenantCustomerRepository;
    private final CustomerAddressRepository customerAddressRepository;
    private final ProductPricingService pricingService;
    private final TaxCalculationService taxCalculationService;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    @Override
    @Transactional
    public OrderResponse create(OrderCreateRequest request, UUID customerId) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new BadRequestException("La orden debe contener al menos un producto.");
        }

        UUID tenantId = request.tenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado con ID: " + tenantId));

        TenantCustomer customer = null;
        if (customerId != null) {
            customer = tenantCustomerRepository.findById(customerId)
                    .orElse(null);
        }

        CustomerAddress address = null;
        if (request.customerAddressId() != null) {
            address = customerAddressRepository.findById(request.customerAddressId())
                    .orElse(null);
        }

        String orderNumber = generateOrderNumber(tenantId);

        Order order = Order.builder()
                .tenant(tenant)
                .customer(customer)
                .address(address)
                .orderNumber(orderNumber)
                .deliveryType(request.deliveryType() != null ? request.deliveryType() : DeliveryType.DELIVERY)
                .status(OrderStatus.PENDIENTE)
                .deliveryAddress(request.deliveryAddress())
                .deliveryLocation(GeoUtils.createPoint(request.deliveryLatitude(), request.deliveryLongitude()))
                .deliveryFee(BigDecimal.ZERO.setScale(2))
                .subtotal(BigDecimal.ZERO.setScale(2))
                .taxTotal(BigDecimal.ZERO.setScale(2))
                .notes(request.notes())
                .items(new ArrayList<>())
                .build();

        BigDecimal accumulatedSubtotal = BigDecimal.ZERO;
        BigDecimal accumulatedTaxTotal = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.items()) {
            // Validación estricta multi-tenant: el producto debe pertenecer a este tenant
            Product product = productRepository.findByIdAndTenantId(itemRequest.productId(), tenantId)
                    .orElseThrow(() -> new BadRequestException(
                            "El producto con ID " + itemRequest.productId() + " no existe o no pertenece a la tienda actual."
                    ));

            if (!Boolean.TRUE.equals(product.getIsAvailable())) {
                throw new BadRequestException("El producto '" + product.getName() + "' no está disponible actualmente.");
            }

            // El frontend NO suministra precios ni impuestos: se calculan y congelan desde el backend
            BigDecimal sellingPrice = pricingService.calculateCurrentPrice(product);
            TaxCalculationResult calc = taxCalculationService.calculate(sellingPrice, product.getTax(), itemRequest.quantity());

            OrderItem orderItem = orderItemMapper.toEntity(itemRequest, product, order, calc);
            order.getItems().add(orderItem);

            accumulatedSubtotal = accumulatedSubtotal.add(calc.subtotal());
            accumulatedTaxTotal = accumulatedTaxTotal.add(calc.taxAmount());
        }

        order.setSubtotal(accumulatedSubtotal);
        order.setTaxTotal(accumulatedTaxTotal);
        // Columna virtual en memoria para la respuesta previa al flush/generación de BD:
        BigDecimal total = accumulatedSubtotal.add(accumulatedTaxTotal).add(order.getDeliveryFee());
        order.setTotal(total);

        Order savedOrder = orderRepository.save(order);
        return orderMapper.toResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse findByIdAndTenant(UUID orderId, UUID tenantId) {
        Order order = orderRepository.findByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada con ID: " + orderId));
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> findAllByTenant(UUID tenantId) {
        return orderRepository.findAllByTenantId(tenantId)
                .stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    private String generateOrderNumber(UUID tenantId) {
        long timestamp = Instant.now().toEpochMilli() % 1000000;
        String shortId = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return "ORD-" + timestamp + "-" + shortId;
    }
}
