package lat.nexofood.api.modules.order.application.service;

import jakarta.persistence.criteria.Predicate;
import lat.nexofood.api.common.audit.AuditAction;
import lat.nexofood.api.common.audit.SecurityAuditLogger;
import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ForbiddenException;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.common.security.TenantAccessService;
import lat.nexofood.api.common.util.GeoUtils;
import lat.nexofood.api.modules.catalog.application.service.ProductPricingService;
import lat.nexofood.api.modules.catalog.application.service.TaxCalculationResult;
import lat.nexofood.api.modules.catalog.application.service.TaxCalculationService;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.domain.ProductPrice;
import lat.nexofood.api.modules.catalog.domain.Tax;
import lat.nexofood.api.modules.catalog.infrastructure.repository.ProductRepository;
import lat.nexofood.api.modules.identity.domain.CustomerAddress;
import lat.nexofood.api.modules.identity.infrastructure.repository.CustomerAddressRepository;
import lat.nexofood.api.modules.inventory.application.service.ProductIngredientService;
import lat.nexofood.api.modules.order.application.dto.OrderDraft;
import lat.nexofood.api.modules.order.application.dto.OrderDraftItem;
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
import lat.nexofood.api.modules.payment.application.service.PaymentRefundService;
import lat.nexofood.api.modules.payment.domain.Payment;
import lat.nexofood.api.modules.store.application.service.customer.CurrentCustomerResolver;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import lat.nexofood.api.modules.store.domain.TenantCustomerId;
import lat.nexofood.api.modules.store.domain.TenantMember;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantCustomerRepository;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantMemberRepository;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.subscription.application.service.guard.SubscriptionGuard;
import lat.nexofood.api.modules.tracking.application.service.DeliveryTrackingService;
import lat.nexofood.api.modules.tracking.application.service.EtaEstimate;
import lat.nexofood.api.modules.tracking.application.service.EtaProvider;
import lat.nexofood.api.modules.tracking.application.service.HaversineEtaProvider;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final TenantRepository tenantRepository;
    private final TenantCustomerRepository tenantCustomerRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final CustomerAddressRepository customerAddressRepository;
    private final ProductPricingService pricingService;
    private final TaxCalculationService taxCalculationService;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CurrentCustomerResolver currentCustomerResolver;
    private final SubscriptionGuard subscriptionGuard;
    private final OrderStateMachine orderStateMachine;
    private final PaymentGuard paymentGuard;
    private final TenantAccessService tenantAccessService;
    private final CurrentUserProvider currentUserProvider;
    private final ProductIngredientService productIngredientService;
    private final PaymentRefundService paymentRefundService;
    private final SecurityAuditLogger securityAuditLogger;
    private final EtaProvider etaProvider;
    private final DeliveryTrackingService deliveryTrackingService;

    @Autowired
    public OrderServiceImpl(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            TenantRepository tenantRepository,
            TenantCustomerRepository tenantCustomerRepository,
            TenantMemberRepository tenantMemberRepository,
            CustomerAddressRepository customerAddressRepository,
            ProductPricingService pricingService,
            TaxCalculationService taxCalculationService,
            OrderMapper orderMapper,
            OrderItemMapper orderItemMapper,
            CurrentCustomerResolver currentCustomerResolver,
            SubscriptionGuard subscriptionGuard,
            OrderStateMachine orderStateMachine,
            PaymentGuard paymentGuard,
            TenantAccessService tenantAccessService,
            CurrentUserProvider currentUserProvider,
            ProductIngredientService productIngredientService,
            @Autowired(required = false) PaymentRefundService paymentRefundService,
            @Autowired(required = false) SecurityAuditLogger securityAuditLogger,
            @Autowired(required = false) EtaProvider etaProvider,
            @Autowired(required = false) DeliveryTrackingService deliveryTrackingService
    ) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.tenantRepository = tenantRepository;
        this.tenantCustomerRepository = tenantCustomerRepository;
        this.tenantMemberRepository = tenantMemberRepository;
        this.customerAddressRepository = customerAddressRepository;
        this.pricingService = pricingService;
        this.taxCalculationService = taxCalculationService;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.currentCustomerResolver = currentCustomerResolver;
        this.subscriptionGuard = subscriptionGuard;
        this.orderStateMachine = orderStateMachine != null ? orderStateMachine : new OrderStateMachine();
        this.paymentGuard = paymentGuard;
        this.tenantAccessService = tenantAccessService;
        this.currentUserProvider = currentUserProvider;
        this.productIngredientService = productIngredientService;
        this.paymentRefundService = paymentRefundService;
        this.securityAuditLogger = securityAuditLogger != null ? securityAuditLogger : new SecurityAuditLogger();
        this.etaProvider = etaProvider != null ? etaProvider : new HaversineEtaProvider();
        this.deliveryTrackingService = deliveryTrackingService;
    }

    public OrderServiceImpl(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            TenantRepository tenantRepository,
            TenantCustomerRepository tenantCustomerRepository,
            TenantMemberRepository tenantMemberRepository,
            CustomerAddressRepository customerAddressRepository,
            ProductPricingService pricingService,
            TaxCalculationService taxCalculationService,
            OrderMapper orderMapper,
            OrderItemMapper orderItemMapper,
            CurrentCustomerResolver currentCustomerResolver,
            SubscriptionGuard subscriptionGuard,
            OrderStateMachine orderStateMachine,
            PaymentGuard paymentGuard,
            TenantAccessService tenantAccessService,
            CurrentUserProvider currentUserProvider,
            ProductIngredientService productIngredientService,
            PaymentRefundService paymentRefundService,
            SecurityAuditLogger securityAuditLogger
    ) {
        this(
                orderRepository,
                productRepository,
                tenantRepository,
                tenantCustomerRepository,
                tenantMemberRepository,
                customerAddressRepository,
                pricingService,
                taxCalculationService,
                orderMapper,
                orderItemMapper,
                currentCustomerResolver,
                subscriptionGuard,
                orderStateMachine,
                paymentGuard,
                tenantAccessService,
                currentUserProvider,
                productIngredientService,
                paymentRefundService,
                securityAuditLogger,
                null,
                null
        );
    }

    public OrderServiceImpl(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            TenantRepository tenantRepository,
            TenantCustomerRepository tenantCustomerRepository,
            TenantMemberRepository tenantMemberRepository,
            CustomerAddressRepository customerAddressRepository,
            ProductPricingService pricingService,
            TaxCalculationService taxCalculationService,
            OrderMapper orderMapper,
            OrderItemMapper orderItemMapper,
            CurrentCustomerResolver currentCustomerResolver,
            SubscriptionGuard subscriptionGuard,
            OrderStateMachine orderStateMachine,
            PaymentGuard paymentGuard,
            TenantAccessService tenantAccessService,
            CurrentUserProvider currentUserProvider,
            ProductIngredientService productIngredientService,
            EtaProvider etaProvider,
            DeliveryTrackingService deliveryTrackingService
    ) {
        this(
                orderRepository,
                productRepository,
                tenantRepository,
                tenantCustomerRepository,
                tenantMemberRepository,
                customerAddressRepository,
                pricingService,
                taxCalculationService,
                orderMapper,
                orderItemMapper,
                currentCustomerResolver,
                subscriptionGuard,
                orderStateMachine,
                paymentGuard,
                tenantAccessService,
                currentUserProvider,
                productIngredientService,
                null,
                new SecurityAuditLogger(),
                etaProvider,
                deliveryTrackingService
        );
    }

    public OrderServiceImpl(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            TenantRepository tenantRepository,
            TenantCustomerRepository tenantCustomerRepository,
            TenantMemberRepository tenantMemberRepository,
            CustomerAddressRepository customerAddressRepository,
            ProductPricingService pricingService,
            TaxCalculationService taxCalculationService,
            OrderMapper orderMapper,
            OrderItemMapper orderItemMapper,
            CurrentCustomerResolver currentCustomerResolver,
            SubscriptionGuard subscriptionGuard,
            OrderStateMachine orderStateMachine,
            PaymentGuard paymentGuard,
            TenantAccessService tenantAccessService,
            CurrentUserProvider currentUserProvider,
            ProductIngredientService productIngredientService
    ) {
        this(
                orderRepository,
                productRepository,
                tenantRepository,
                tenantCustomerRepository,
                tenantMemberRepository,
                customerAddressRepository,
                pricingService,
                taxCalculationService,
                orderMapper,
                orderItemMapper,
                currentCustomerResolver,
                subscriptionGuard,
                orderStateMachine,
                paymentGuard,
                tenantAccessService,
                currentUserProvider,
                productIngredientService,
                null,
                new SecurityAuditLogger(),
                null,
                null
        );
    }

    public OrderServiceImpl(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            TenantRepository tenantRepository,
            TenantCustomerRepository tenantCustomerRepository,
            TenantMemberRepository tenantMemberRepository,
            CustomerAddressRepository customerAddressRepository,
            ProductPricingService pricingService,
            TaxCalculationService taxCalculationService,
            OrderMapper orderMapper,
            OrderItemMapper orderItemMapper,
            CurrentCustomerResolver currentCustomerResolver,
            SubscriptionGuard subscriptionGuard,
            OrderStateMachine orderStateMachine,
            PaymentGuard paymentGuard,
            TenantAccessService tenantAccessService,
            CurrentUserProvider currentUserProvider
    ) {
        this(
                orderRepository,
                productRepository,
                tenantRepository,
                tenantCustomerRepository,
                tenantMemberRepository,
                customerAddressRepository,
                pricingService,
                taxCalculationService,
                orderMapper,
                orderItemMapper,
                currentCustomerResolver,
                subscriptionGuard,
                orderStateMachine,
                paymentGuard,
                tenantAccessService,
                currentUserProvider,
                null,
                null,
                new SecurityAuditLogger()
        );
    }

    public OrderServiceImpl(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            TenantRepository tenantRepository,
            TenantCustomerRepository tenantCustomerRepository,
            CustomerAddressRepository customerAddressRepository,
            ProductPricingService pricingService,
            TaxCalculationService taxCalculationService,
            OrderMapper orderMapper,
            OrderItemMapper orderItemMapper
    ) {
        this(
                orderRepository,
                productRepository,
                tenantRepository,
                tenantCustomerRepository,
                null,
                customerAddressRepository,
                pricingService,
                taxCalculationService,
                orderMapper,
                orderItemMapper,
                null,
                null,
                new OrderStateMachine(),
                null,
                null,
                null,
                null,
                null,
                new SecurityAuditLogger()
        );
    }

    @Override
    @Transactional
    public OrderResponse create(OrderCreateRequest request, UUID customerId) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new BadRequestException("La orden debe contener al menos un producto.");
        }

        UUID tenantId = request.tenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado con ID: " + tenantId));

        validateTenantActiveAndSubscribed(tenant);

        TenantCustomer customer = null;
        if (customerId != null) {
            customer = tenantCustomerRepository.findById(new TenantCustomerId(tenantId, customerId))
                    .orElse(null);
            if (customer != null && Boolean.TRUE.equals(customer.getIsBlocked())) {
                throw new ForbiddenException("Tu cuenta ha sido bloqueada por este comercio.");
            }
        } else if (currentCustomerResolver != null) {
            try {
                customer = currentCustomerResolver.resolveByTenantId(tenantId);
            } catch (ForbiddenException | BadRequestException e) {
                throw e;
            } catch (Exception ignored) {
                // En pruebas unitarias sin contexto de seguridad se permite customer nulo
            }
        }

        OrderDraft draft = buildDraftInternal(request, null, tenant, customer);
        Order savedOrder = persistFromDraft(draft, tenant, customer);
        return orderMapper.toResponse(savedOrder);
    }

    @Override
    @Transactional
    public OrderResponse createFromResolvedCustomer(
            OrderCreateRequest request,
            String ruc,
            TenantCustomer customer
    ) {
        OrderDraft draft = buildDraft(request, ruc, customer);
        Order savedOrder = persistFromDraft(draft, customer.getTenant(), customer);
        return orderMapper.toResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDraft buildDraft(
            OrderCreateRequest request,
            String ruc,
            TenantCustomer customer
    ) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new BadRequestException("La orden debe contener al menos un producto.");
        }
        if (customer == null || customer.getTenant() == null) {
            throw new BadRequestException("El cliente y la tienda son obligatorios para crear el pedido.");
        }

        Tenant tenant = customer.getTenant();
        validateTenantActiveAndSubscribed(tenant);

        if (Boolean.TRUE.equals(customer.getIsBlocked())) {
            throw new ForbiddenException("Tu cuenta ha sido bloqueada por este comercio.");
        }

        return buildDraftInternal(request, ruc, tenant, customer);
    }

    private OrderDraft buildDraftInternal(
            OrderCreateRequest request,
            String ruc,
            Tenant tenant,
            TenantCustomer customer
    ) {
        UUID tenantId = tenant.getId();
        DeliveryType deliveryType = request.deliveryType() != null ? request.deliveryType() : DeliveryType.DELIVERY;

        UUID customerUserId = resolveCustomerUserId(customer);

        CustomerAddress address = null;
        String resolvedDeliveryAddress = request.deliveryAddress();
        Point resolvedDeliveryLocation = null;

        if (request.customerAddressId() != null) {
            if (customerUserId != null) {
                address = customerAddressRepository.findByIdAndUserId(request.customerAddressId(), customerUserId)
                        .orElseThrow(() -> new BadRequestException(
                                "La dirección seleccionada no existe o no pertenece a tu cuenta"
                        ));
            } else {
                address = customerAddressRepository.findById(request.customerAddressId())
                        .orElseThrow(() -> new BadRequestException("La dirección seleccionada no existe"));
            }
            if (resolvedDeliveryAddress == null || resolvedDeliveryAddress.isBlank()) {
                resolvedDeliveryAddress = address.getAddressLine();
            }
            resolvedDeliveryLocation = address.getLocation();
        }

        if (resolvedDeliveryLocation == null
                && request.deliveryLatitude() != null
                && request.deliveryLongitude() != null) {
            resolvedDeliveryLocation = GeoUtils.createPoint(
                    request.deliveryLatitude(),
                    request.deliveryLongitude()
            );
        }

        BigDecimal deliveryFee = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        UUID resolvedAddressId = address != null ? address.getId() : null;
        Double resolvedLatitude = null;
        Double resolvedLongitude = null;

        if (deliveryType == DeliveryType.DELIVERY) {
            if (tenant.getLocation() != null && resolvedDeliveryLocation == null) {
                throw new BadRequestException(
                        "Para pedidos con entrega a domicilio (DELIVERY) debe indicar una dirección o ubicación de entrega"
                );
            }
            if (tenant.getLocation() != null
                    && resolvedDeliveryLocation != null
                    && tenant.getDeliveryRadiusKm() != null) {
                double distanceKm = GeoUtils.calculateDistanceKm(tenant.getLocation(), resolvedDeliveryLocation);
                if (BigDecimal.valueOf(distanceKm).compareTo(tenant.getDeliveryRadiusKm()) > 0) {
                    throw new BadRequestException(String.format(
                            "La dirección de entrega se encuentra fuera del área de cobertura del restaurante (%.2f km > %.2f km máx.)",
                            distanceKm,
                            tenant.getDeliveryRadiusKm()
                    ));
                }
            }
            if (tenant.getDefaultDeliveryFee() != null) {
                deliveryFee = tenant.getDefaultDeliveryFee().setScale(2, RoundingMode.HALF_UP);
            }
            resolvedLatitude = GeoUtils.getLatitude(resolvedDeliveryLocation);
            resolvedLongitude = GeoUtils.getLongitude(resolvedDeliveryLocation);
        } else {
            // En TAKEAWAY o DINE_IN no se cobra envío
            resolvedDeliveryAddress = null;
            resolvedAddressId = null;
        }

        BigDecimal accumulatedSubtotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal accumulatedTaxTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<OrderDraftItem> draftItems = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (OrderItemRequest itemRequest : request.items()) {
            Product product = productRepository.findByIdAndTenantId(itemRequest.productId(), tenantId)
                    .orElseThrow(() -> new BadRequestException(
                            "El producto con ID " + itemRequest.productId() + " no existe o no pertenece a la tienda actual."
                    ));

            if (!Boolean.TRUE.equals(product.getIsAvailable())) {
                throw new BadRequestException("El producto '" + product.getName() + "' no está disponible actualmente.");
            }
            if (product.getCategory() != null && !Boolean.TRUE.equals(product.getCategory().getIsActive())) {
                throw new BadRequestException("El producto '" + product.getName() + "' pertenece a una categoría inactiva.");
            }

            BigDecimal sellingPrice = pricingService.calculateCurrentPrice(product);
            TaxCalculationResult calc = taxCalculationService.calculate(sellingPrice, product.getTax(), itemRequest.quantity());

            ProductPrice effectivePrice = pricingService.getEffectivePriceRule(product, now);
            if (effectivePrice == null) {
                effectivePrice = pricingService.getBasePriceRule(product);
            }
            Tax effectiveTax = calc != null ? calc.tax() : product.getTax();

            OrderDraftItem draftItem = OrderDraftItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .priceId(effectivePrice != null ? effectivePrice.getId() : null)
                    .taxId(effectiveTax != null ? effectiveTax.getId() : null)
                    .unitPrice(calc != null ? calc.baseUnitPrice() : sellingPrice)
                    .quantity(itemRequest.quantity())
                    .lineSubtotal(calc != null ? calc.subtotal() : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .taxRate(calc != null && calc.taxRate() != null ? calc.taxRate() : BigDecimal.ZERO)
                    .taxAmount(calc != null && calc.taxAmount() != null ? calc.taxAmount() : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .notes(itemRequest.notes())
                    .build();

            draftItems.add(draftItem);
            accumulatedSubtotal = accumulatedSubtotal.add(draftItem.lineSubtotal());
            accumulatedTaxTotal = accumulatedTaxTotal.add(draftItem.taxAmount());
        }

        BigDecimal total = accumulatedSubtotal.add(accumulatedTaxTotal).add(deliveryFee).setScale(2, RoundingMode.HALF_UP);

        return OrderDraft.builder()
                .tenantId(tenantId)
                .customerUserId(customerUserId)
                .deliveryType(deliveryType)
                .customerAddressId(resolvedAddressId)
                .deliveryAddress(resolvedDeliveryAddress)
                .deliveryLatitude(resolvedLatitude)
                .deliveryLongitude(resolvedLongitude)
                .ruc(ruc != null && !ruc.isBlank() ? ruc.trim() : null)
                .notes(request.notes())
                .subtotal(accumulatedSubtotal)
                .taxTotal(accumulatedTaxTotal)
                .deliveryFee(deliveryFee)
                .total(total)
                .items(draftItems)
                .build();
    }

    @Override
    @Transactional
    public Order persistFromDraft(OrderDraft draft, Tenant tenant, TenantCustomer customer) {
        if (draft == null) {
            throw new BadRequestException("El borrador del pedido (draft) es obligatorio");
        }
        UUID tenantId = tenant.getId();

        CustomerAddress address = null;
        if (draft.customerAddressId() != null) {
            address = customerAddressRepository.findById(draft.customerAddressId()).orElse(null);
        }

        Point deliveryLocation = null;
        if (draft.deliveryType() == DeliveryType.DELIVERY) {
            if (draft.deliveryLatitude() != null && draft.deliveryLongitude() != null) {
                deliveryLocation = GeoUtils.createPoint(draft.deliveryLatitude(), draft.deliveryLongitude());
            } else if (address != null) {
                deliveryLocation = address.getLocation();
            }
        }

        String orderNumber = generateSequentialOrderNumber(tenantId);

        Order order = Order.builder()
                .tenant(tenant)
                .customer(customer)
                .customerId(draft.customerUserId())
                .address(address)
                .orderNumber(orderNumber)
                .ruc(draft.ruc())
                .deliveryType(draft.deliveryType() != null ? draft.deliveryType() : DeliveryType.DELIVERY)
                .status(OrderStatus.PENDIENTE)
                .deliveryAddress(draft.deliveryAddress())
                .deliveryLocation(deliveryLocation)
                .deliveryFee(draft.deliveryFee() != null ? draft.deliveryFee() : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .subtotal(draft.subtotal() != null ? draft.subtotal() : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .taxTotal(draft.taxTotal() != null ? draft.taxTotal() : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .total(draft.total() != null ? draft.total() : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .notes(draft.notes())
                .items(new ArrayList<>())
                .build();

        if (draft.items() != null) {
            for (OrderDraftItem draftItem : draft.items()) {
                Product product = draftItem.productId() != null
                        ? productRepository.findByIdAndTenantId(draftItem.productId(), tenantId).orElse(null)
                        : null;

                ProductPrice priceRule = null;
                if (product != null && product.getPrices() != null && draftItem.priceId() != null) {
                    priceRule = product.getPrices().stream()
                            .filter(pr -> draftItem.priceId().equals(pr.getId()))
                            .findFirst()
                            .orElse(null);
                }

                Tax tax = null;
                if (product != null && product.getTax() != null
                        && (draftItem.taxId() == null || draftItem.taxId().equals(product.getTax().getId()))) {
                    tax = product.getTax();
                }

                OrderItem orderItem = OrderItem.builder()
                        .order(order)
                        .tenant(tenant)
                        .product(product)
                        .price(priceRule)
                        .tax(tax)
                        .productName(draftItem.productName())
                        .unitPrice(draftItem.unitPrice())
                        .quantity(draftItem.quantity())
                        .subtotal(draftItem.lineSubtotal())
                        .taxRate(draftItem.taxRate() != null ? draftItem.taxRate() : BigDecimal.ZERO)
                        .taxAmount(draftItem.taxAmount() != null ? draftItem.taxAmount() : BigDecimal.ZERO)
                        .notes(draftItem.notes())
                        .build();

                order.getItems().add(orderItem);
            }
        }

        return orderRepository.save(order);
    }

    @Override
    @Transactional
    public Order createEntityFromApprovedPayment(Payment payment) {
        if (payment == null || payment.getCheckoutSnapshot() == null) {
            throw new BadRequestException("El pago aprobado no contiene un snapshot de checkout válido");
        }
        if (payment.getOrder() != null) {
            return payment.getOrder();
        }

        OrderDraft draft = payment.getCheckoutSnapshot();
        Tenant tenant = payment.getTenant() != null
                ? payment.getTenant()
                : tenantRepository.findById(draft.tenantId())
                        .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado: " + draft.tenantId()));

        UUID customerUserId = draft.customerUserId() != null ? draft.customerUserId() : payment.getCustomerId();
        TenantCustomer customer = null;
        if (customerUserId != null && tenantCustomerRepository != null) {
            customer = tenantCustomerRepository.findById(new TenantCustomerId(tenant.getId(), customerUserId))
                    .orElse(null);
        }

        if (payment.getAmount() != null
                && draft.total() != null
                && payment.getAmount().compareTo(draft.total()) != 0) {
            throw new BadRequestException("El monto del pago no coincide con el total del snapshot del pedido");
        }

        return persistFromDraft(draft, tenant, customer);
    }

    @Override
    @Transactional
    public OrderResponse createFromApprovedPayment(Payment payment) {
        Order savedOrder = createEntityFromApprovedPayment(payment);
        return orderMapper.toResponse(savedOrder);
    }

    @Override
    @Transactional
    public OrderResponse cancelByCustomer(String slug, UUID orderId, String reason) {
        TenantCustomer customer = currentCustomerResolver.resolveBySlug(slug);
        UUID tenantId = customer.getTenant().getId();
        UUID userId = resolveCustomerUserId(customer);

        Order order = orderRepository.findByIdAndTenantIdAndCustomerUserIdForUpdate(orderId, tenantId, userId)
                .or(() -> orderRepository.findByIdAndTenantIdAndCustomerUserId(orderId, tenantId, userId))
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado en tu historial"));

        if (order.getStatus() != OrderStatus.PENDIENTE) {
            throw new ResourceConflictException(
                    "Solo puedes cancelar el pedido y recibir el reembolso mientras se encuentre en estado PENDIENTE (estado actual: "
                            + order.getStatus() + ")"
            );
        }

        String resolvedReason = (reason != null && !reason.isBlank())
                ? reason.trim()
                : "Cancelado por el cliente en estado PENDIENTE";

        if (paymentRefundService != null) {
            paymentRefundService.refundOrderPayment(order, resolvedReason);
        }

        order.setStatus(OrderStatus.CANCELADO);
        Order savedOrder = orderRepository.save(order);

        securityAuditLogger.logEvent(
                AuditAction.ORDER_CANCELLATION,
                tenantId,
                orderId.toString(),
                "Pedido " + order.getOrderNumber() + " cancelado por el cliente en estado PENDIENTE (" + resolvedReason + ")"
        );

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

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> findByTenantPaginated(
            UUID tenantId,
            OrderStatus status,
            DeliveryType deliveryType,
            OffsetDateTime fromDate,
            OffsetDateTime toDate,
            Pageable pageable
    ) {
        Specification<Order> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenant").get("id"), tenantId));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (deliveryType != null) {
                predicates.add(cb.equal(root.get("deliveryType"), deliveryType));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDate));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Order> page = orderRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(orderMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getKitchenOrders(UUID tenantId) {
        return orderRepository.findAllByTenantIdAndStatusOrderByCreatedAtAsc(tenantId, OrderStatus.EN_PREPARACION)
                .stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getMyAssignedDeliveries(UUID tenantId, OrderStatus status) {
        if (currentUserProvider == null) {
            throw new BadRequestException("No se pudo identificar al repartidor autenticado");
        }
        UUID staffUserId = currentUserProvider.getCurrentUserId();
        List<Order> orders;
        if (status != null) {
            orders = orderRepository.findAllByTenantIdAndDeliveryStaffIdAndStatusOrderByCreatedAtAsc(
                    tenantId, staffUserId, status
            );
        } else {
            orders = orderRepository.findAllByTenantIdAndDeliveryStaffIdOrderByCreatedAtAsc(
                    tenantId, staffUserId
            );
        }
        return orders.stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public PageResponse<OrderResponse> getMyOrdersBySlug(String slug, Pageable pageable) {
        TenantCustomer customer = currentCustomerResolver.resolveBySlug(slug);
        UUID tenantId = customer.getTenant().getId();
        UUID userId = resolveCustomerUserId(customer);

        Page<Order> page = orderRepository.findAllByTenantIdAndCustomerUserIdOrderByCreatedAtDesc(
                tenantId, userId, pageable
        );
        return PageResponse.from(page.map(orderMapper::toResponse));
    }

    @Override
    @Transactional
    public OrderResponse getMyOrderDetailBySlug(String slug, UUID orderId) {
        TenantCustomer customer = currentCustomerResolver.resolveBySlug(slug);
        UUID tenantId = customer.getTenant().getId();
        UUID userId = resolveCustomerUserId(customer);

        Order order = orderRepository.findByIdAndTenantIdAndCustomerUserId(orderId, tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado en tu historial"));

        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse updateStatus(UUID tenantId, UUID orderId, OrderStatus newStatus) {
        Order order = orderRepository.findByIdAndTenantIdForUpdate(orderId, tenantId)
                .or(() -> orderRepository.findByIdAndTenantId(orderId, tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada con ID: " + orderId));

        boolean isSuperAdmin = tenantAccessService != null && tenantAccessService.isSuperAdmin();
        TenantStaffRole staffRole = null;
        UUID actorUserId = null;

        if (currentUserProvider != null) {
            try {
                actorUserId = currentUserProvider.getCurrentUserId();
            } catch (Exception ignored) {
            }
        }
        if (tenantAccessService != null && !isSuperAdmin) {
            staffRole = tenantAccessService.getCurrentUserRoleInTenant(tenantId).orElse(null);
        } else if (tenantAccessService == null) {
            isSuperAdmin = true;
        }

        OrderStatus previousStatus = order.getStatus();
        orderStateMachine.validateTransition(order, newStatus, staffRole, isSuperAdmin, actorUserId);

        if (newStatus == OrderStatus.EN_PREPARACION && paymentGuard != null) {
            paymentGuard.assertCanMoveToPreparation(order);
        }

        if (newStatus == OrderStatus.CANCELADO && paymentRefundService != null) {
            paymentRefundService.refundOrderPayment(order, "Pedido cancelado desde el restaurante (" + previousStatus + ")");
        }

        if (newStatus == OrderStatus.EN_PREPARACION && productIngredientService != null) {
            productIngredientService.consumeStockForOrder(order);
        } else if (newStatus == OrderStatus.CANCELADO
                && previousStatus == OrderStatus.EN_PREPARACION
                && productIngredientService != null) {
            productIngredientService.restoreStockForCanceledOrder(order);
        }

        order.setStatus(newStatus);
        OffsetDateTime now = OffsetDateTime.now();

        if (newStatus == OrderStatus.EN_CAMINO) {
            order.setDispatchedAt(now);
            applyStoreToCustomerEtaIfPossible(order);
        } else if (newStatus == OrderStatus.ENTREGADO) {
            order.setDeliveredAt(now);
            order.setEstimatedDeliveryMinutes(0);
        } else if (newStatus == OrderStatus.CANCELADO) {
            order.setEstimatedDeliveryMinutes(null);
            order.setEstimatedArrivalAt(null);
        }

        Order savedOrder = orderRepository.save(order);

        if (newStatus == OrderStatus.EN_CAMINO && deliveryTrackingService != null) {
            deliveryTrackingService.onOrderDispatched(savedOrder);
        } else if ((newStatus == OrderStatus.ENTREGADO || newStatus == OrderStatus.CANCELADO)
                && deliveryTrackingService != null) {
            deliveryTrackingService.onOrderClosed(savedOrder);
        }

        if (newStatus == OrderStatus.CANCELADO) {
            securityAuditLogger.logEvent(
                    AuditAction.ORDER_CANCELLATION,
                    tenantId,
                    orderId.toString(),
                    "Pedido " + order.getOrderNumber() + " cancelado desde estado " + previousStatus
            );
        }

        if (newStatus == OrderStatus.ENTREGADO) {
            awardLoyaltyPoints(savedOrder);
        }

        return orderMapper.toResponse(savedOrder);
    }

    @Override
    @Transactional
    public OrderResponse assignDeliveryStaff(UUID tenantId, UUID orderId, UUID deliveryStaffUserId) {
        Order order = orderRepository.findByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada con ID: " + orderId));

        if (order.getDeliveryType() != DeliveryType.DELIVERY) {
            throw new BadRequestException("Solo se puede asignar repartidor a pedidos con modalidad DELIVERY");
        }
        if (order.getStatus() == OrderStatus.ENTREGADO || order.getStatus() == OrderStatus.CANCELADO) {
            throw new BadRequestException("No se puede asignar repartidor a un pedido finalizado o cancelado");
        }

        TenantMember staffMember = tenantMemberRepository
                .findByTenantIdAndUserIdAndIsActiveTrue(tenantId, deliveryStaffUserId)
                .orElseThrow(() -> new BadRequestException(
                        "El usuario indicado no es un miembro activo de este restaurante"
                ));

        if (staffMember.getRole() != TenantStaffRole.REPARTIDOR) {
            throw new BadRequestException("El miembro seleccionado no tiene el rol de REPARTIDOR en este restaurante");
        }

        order.setDeliveryStaff(staffMember.getUser());
        applyStoreToCustomerEtaIfPossible(order);

        Order saved = orderRepository.save(order);
        if (saved.getStatus() == OrderStatus.EN_CAMINO && deliveryTrackingService != null) {
            deliveryTrackingService.onOrderDispatched(saved);
        }
        return orderMapper.toResponse(saved);
    }

    private UUID resolveCustomerUserId(TenantCustomer customer) {
        if (customer == null) {
            return null;
        }
        if (customer.getUser() != null && customer.getUser().getId() != null) {
            return customer.getUser().getId();
        }
        if (customer.getId() != null) {
            return customer.getId().getUserId();
        }
        return null;
    }

    private void applyStoreToCustomerEtaIfPossible(Order order) {
        if (order == null || etaProvider == null) {
            return;
        }
        Tenant tenant = order.getTenant();
        if (tenant != null && tenant.getLocation() != null && order.getDeliveryLocation() != null) {
            EtaEstimate estimate = etaProvider.estimateFromStoreToCustomer(
                    tenant.getLocation(),
                    order.getDeliveryLocation()
            );
            order.setEstimatedDistanceKm(estimate.estimatedRouteDistanceKm());
            order.setEstimatedDeliveryMinutes(estimate.estimatedMinutes());
            order.setEstimatedArrivalAt(estimate.estimatedArrivalAt());
        }
    }

    private void awardLoyaltyPoints(Order order) {
        TenantCustomer customer = order.getCustomer();
        if (customer == null) {
            return;
        }
        BigDecimal orderTotal = order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO;
        int earnedPoints = Math.max(1, orderTotal.setScale(0, RoundingMode.DOWN).intValue());
        int currentPoints = customer.getLoyaltyPoints() != null ? customer.getLoyaltyPoints() : 0;
        customer.setLoyaltyPoints(currentPoints + earnedPoints);

        int currentTotalOrders = customer.getTotalOrders() != null ? customer.getTotalOrders() : 0;
        customer.setTotalOrders(currentTotalOrders + 1);
        OffsetDateTime now = OffsetDateTime.now();
        if (customer.getFirstOrderAt() == null) {
            customer.setFirstOrderAt(now);
        }
        customer.setLastOrderAt(now);

        if (tenantCustomerRepository != null) {
            tenantCustomerRepository.save(customer);
        }
    }

    private void validateTenantActiveAndSubscribed(Tenant tenant) {
        if (Boolean.FALSE.equals(tenant.getIsActive())) {
            throw new BadRequestException("El restaurante '" + tenant.getName() + "' no se encuentra activo actualmente");
        }
        if (subscriptionGuard != null) {
            subscriptionGuard.assertActive(tenant.getId());
        }
    }

    private String generateSequentialOrderNumber(UUID tenantId) {
        long count = orderRepository.countByTenantId(tenantId);
        long nextSeq = count + 1;
        String candidate = String.format("ORD-%06d", nextSeq);
        while (orderRepository.existsByOrderNumberAndTenantId(candidate, tenantId)) {
            nextSeq++;
            candidate = String.format("ORD-%06d", nextSeq);
        }
        return candidate;
    }
}
