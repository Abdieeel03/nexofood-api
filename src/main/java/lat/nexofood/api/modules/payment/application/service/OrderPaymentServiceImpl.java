package lat.nexofood.api.modules.payment.application.service;

import lat.nexofood.api.common.config.TenantConnectionPreparer;
import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.PaymentGatewayException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.common.security.TenantContext;
import lat.nexofood.api.modules.cart.domain.Cart;
import lat.nexofood.api.modules.cart.infrastructure.repository.CartItemRepository;
import lat.nexofood.api.modules.cart.infrastructure.repository.CartRepository;
import lat.nexofood.api.modules.order.application.dto.OrderDraft;
import lat.nexofood.api.modules.order.application.dto.OrderDraftItem;
import lat.nexofood.api.modules.order.application.service.OrderService;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.order.domain.OrderStatus;
import lat.nexofood.api.modules.order.infrastructure.repository.OrderRepository;
import lat.nexofood.api.modules.payment.domain.MpOrderStatusMapper;
import lat.nexofood.api.modules.payment.domain.Payment;
import lat.nexofood.api.modules.payment.domain.PaymentStatus;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.FrontendProperties;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.MercadoPagoClient;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.MercadoPagoProperties;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpOrderRequest;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpOrderRequest.MpBackUrls;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpOrderRequest.MpConfig;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpOrderRequest.MpItem;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpOrderRequest.MpOnlineConfig;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpOrderRequest.MpPayer;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpOrderResponse;
import lat.nexofood.api.modules.payment.infrastructure.repository.PaymentRepository;
import lat.nexofood.api.modules.payment.web.dto.response.CheckoutPaymentResponse;
import lat.nexofood.api.modules.payment.web.dto.response.PaymentResponse;
import lat.nexofood.api.modules.payment.web.dto.response.PaymentStatusResponse;
import lat.nexofood.api.modules.payment.web.mapper.PaymentMapper;
import lat.nexofood.api.modules.store.application.service.customer.CurrentCustomerResolver;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class OrderPaymentServiceImpl implements OrderPaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final MercadoPagoClient mercadoPagoClient;
    private final MercadoPagoOAuthService oauthService;
    private final PaymentRefundService paymentRefundService;
    private final MercadoPagoProperties properties;
    private final FrontendProperties frontendProperties;
    private final CurrentCustomerResolver currentCustomerResolver;
    private final PaymentMapper paymentMapper;
    private final TenantConnectionPreparer tenantConnectionPreparer;

    @Autowired
    public OrderPaymentServiceImpl(
            PaymentRepository paymentRepository,
            OrderService orderService,
            OrderRepository orderRepository,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            MercadoPagoClient mercadoPagoClient,
            MercadoPagoOAuthService oauthService,
            PaymentRefundService paymentRefundService,
            MercadoPagoProperties properties,
            FrontendProperties frontendProperties,
            CurrentCustomerResolver currentCustomerResolver,
            PaymentMapper paymentMapper,
            @Autowired(required = false) TenantConnectionPreparer tenantConnectionPreparer
    ) {
        this.paymentRepository = paymentRepository;
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.mercadoPagoClient = mercadoPagoClient;
        this.oauthService = oauthService;
        this.paymentRefundService = paymentRefundService;
        this.properties = properties;
        this.frontendProperties = frontendProperties;
        this.currentCustomerResolver = currentCustomerResolver;
        this.paymentMapper = paymentMapper;
        this.tenantConnectionPreparer = tenantConnectionPreparer;
    }

    @Override
    @Transactional
    public CheckoutPaymentResponse startCheckout(OrderDraft draft, TenantCustomer customer) {
        if (draft == null || customer == null || customer.getTenant() == null) {
            throw new BadRequestException("Los datos del pedido y del cliente son obligatorios para iniciar el pago");
        }

        Tenant tenant = customer.getTenant();
        String accessToken = oauthService.getValidAccessToken(tenant);

        UUID customerUserId = draft.customerUserId() != null
                ? draft.customerUserId()
                : (customer.getUser() != null ? customer.getUser().getId() : customer.getId().getUserId());

        // Cancelar intentos PENDING previos del mismo cliente en la tienda para evitar cobros duplicados
        cancelPreviousPendingAttempts(tenant, customerUserId, accessToken);

        Duration expirationDuration = parseOrderExpirationDuration();
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(expirationDuration);
        String currency = properties.getCurrency() != null && !properties.getCurrency().isBlank()
                ? properties.getCurrency()
                : "PEN";

        Payment payment = Payment.builder()
                .tenant(tenant)
                .customerId(customerUserId)
                .status(PaymentStatus.PENDING)
                .amount(draft.total().setScale(2, RoundingMode.HALF_UP))
                .currency(currency)
                .checkoutSnapshot(draft)
                .expiresAt(expiresAt)
                .build();

        Payment savedPayment = paymentRepository.save(payment);

        String customerEmail = customer.getUser() != null && customer.getUser().getEmail() != null
                ? customer.getUser().getEmail()
                : "cliente@nexofood.lat";

        MpOrderRequest mpOrderRequest = buildMpOrderRequest(
                savedPayment.getId(),
                tenant.getSlug(),
                customerEmail,
                draft
        );

        MpOrderResponse mpResponse = mercadoPagoClient.createOrder(
                accessToken,
                savedPayment.getId().toString(),
                mpOrderRequest
        );

        if (mpResponse == null || mpResponse.id() == null || mpResponse.checkoutUrl() == null) {
            throw new PaymentGatewayException("Mercado Pago no devolvió un enlace de pago válido (checkout_url)");
        }

        savedPayment.setMpOrderId(mpResponse.id());
        savedPayment.setCheckoutUrl(mpResponse.checkoutUrl());
        savedPayment.setStatusDetail(mpResponse.statusDetail());
        if (mpResponse.rawJson() != null) {
            savedPayment.setRawResponse(paymentMapper.sanitizePaymentResponse(mpResponse.rawJson()));
        }

        Payment updatedPayment = paymentRepository.save(savedPayment);

        return CheckoutPaymentResponse.builder()
                .paymentId(updatedPayment.getId())
                .mpOrderId(updatedPayment.getMpOrderId())
                .checkoutUrl(updatedPayment.getCheckoutUrl())
                .status(updatedPayment.getStatus())
                .amount(updatedPayment.getAmount())
                .currency(updatedPayment.getCurrency())
                .expiresAt(updatedPayment.getExpiresAt())
                .build();
    }

    @Override
    @Transactional
    public void processMpOrderNotification(String mpOrderId) {
        if (mpOrderId == null || mpOrderId.isBlank()) {
            return;
        }

        Optional<Payment> paymentOpt = paymentRepository.findByMpOrderIdForUpdate(mpOrderId)
                .or(() -> paymentRepository.findByMpOrderId(mpOrderId));

        if (paymentOpt.isEmpty()) {
            log.debug("Notificación de Mercado Pago ignorada: mpOrderId={} no corresponde a ningún pago local", mpOrderId);
            return;
        }

        Payment payment = paymentOpt.get();
        Tenant tenant = payment.getTenant();
        UUID previousTenantContext = TenantContext.getTenantId();
        try {
            if (tenant != null && tenant.getId() != null) {
                TenantContext.setTenantId(tenant.getId());
                if (tenantConnectionPreparer != null) {
                    tenantConnectionPreparer.setTenantContext(tenant.getId());
                }
            }

            String accessToken = oauthService.getValidAccessToken(tenant);
            MpOrderResponse mpOrder = mercadoPagoClient.getOrder(accessToken, mpOrderId);
            if (mpOrder == null) {
                return;
            }

            if (mpOrder.externalReference() != null
                    && !mpOrder.externalReference().isBlank()
                    && !mpOrder.externalReference().equals(payment.getId().toString())) {
                log.error("[SECURITY] Discrepancia de external_reference en mpOrderId={}: esperado={}, recibido={}",
                        mpOrderId, payment.getId(), mpOrder.externalReference());
                return;
            }

            PaymentStatus newStatus = MpOrderStatusMapper.toPaymentStatus(mpOrder.status(), mpOrder.statusDetail());
            payment.setStatusDetail(mpOrder.statusDetail());

            if (mpOrder.firstPaymentId() != null) {
                payment.setMpPaymentId(mpOrder.firstPaymentId());
            }
            if (mpOrder.firstPaymentMethodId() != null) {
                payment.setPaymentMethod(mpOrder.firstPaymentMethodId());
            }
            if (mpOrder.rawJson() != null) {
                payment.setRawResponse(paymentMapper.sanitizePaymentResponse(mpOrder.rawJson()));
            }

            if (newStatus == PaymentStatus.APPROVED) {
                BigDecimal reportedAmount = mpOrder.totalPaidAmount() != null && mpOrder.totalPaidAmount().compareTo(BigDecimal.ZERO) > 0
                        ? mpOrder.totalPaidAmount()
                        : mpOrder.totalAmount();

                if (reportedAmount != null && reportedAmount.compareTo(payment.getAmount()) != 0) {
                    log.error("[SECURITY] Discrepancia de monto en mpOrderId={}: esperado={}, pagado={}",
                            mpOrderId, payment.getAmount(), reportedAmount);
                    return;
                }

                payment.setStatus(PaymentStatus.APPROVED);
                if (payment.getApprovedAt() == null) {
                    payment.setApprovedAt(OffsetDateTime.now());
                }

                if (payment.getOrder() == null) {
                    try {
                        Order createdOrder = orderService.createEntityFromApprovedPayment(payment);
                        payment.setOrder(createdOrder);
                        clearCustomerCartAfterApprovedPayment(tenant.getId(), payment.getCustomerId());
                        log.info("Orden {} ({}) generada exitosamente tras confirmación de pago {} (mpOrderId={})",
                                createdOrder.getId(), createdOrder.getOrderNumber(), payment.getId(), mpOrderId);
                    } catch (BadRequestException | ResourceNotFoundException ex) {
                        log.error("Fallo irrecuperable al crear orden para el pago aprobado {} (mpOrderId={}). Se ejecuta reembolso automático.",
                                payment.getId(), mpOrderId, ex);
                        paymentRepository.save(payment);
                        paymentRefundService.refundPaymentDirectly(payment, "order_creation_failed: " + ex.getMessage());
                        return;
                    }
                }
            } else if (newStatus == PaymentStatus.REFUNDED) {
                payment.setStatus(PaymentStatus.REFUNDED);
                if (payment.getRefundedAt() == null) {
                    payment.setRefundedAt(OffsetDateTime.now());
                }
                Order linkedOrder = payment.getOrder();
                if (linkedOrder != null && linkedOrder.getStatus() == OrderStatus.PENDIENTE) {
                    linkedOrder.setStatus(OrderStatus.CANCELADO);
                    orderRepository.save(linkedOrder);
                }
            } else if (payment.getStatus() != PaymentStatus.APPROVED && payment.getStatus() != PaymentStatus.REFUNDED) {
                payment.setStatus(newStatus);
            }

            paymentRepository.save(payment);
        } finally {
            if (previousTenantContext == null) {
                TenantContext.clear();
            } else {
                TenantContext.setTenantId(previousTenantContext);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentStatusResponse getCustomerPaymentStatus(String slug, UUID paymentId) {
        TenantCustomer customer = currentCustomerResolver.resolveBySlug(slug);
        UUID tenantId = customer.getTenant().getId();
        UUID userId = customer.getUser() != null && customer.getUser().getId() != null
                ? customer.getUser().getId()
                : customer.getId().getUserId();

        Payment payment = paymentRepository.findByIdAndTenantIdAndCustomerId(paymentId, tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Pago no encontrado en tu historial"));

        return PaymentStatusResponse.builder()
                .paymentId(payment.getId())
                .status(payment.getStatus())
                .statusDetail(payment.getStatusDetail())
                .orderId(payment.getOrder() != null ? payment.getOrder().getId() : null)
                .orderNumber(payment.getOrder() != null ? payment.getOrder().getOrderNumber() : null)
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .approvedAt(payment.getApprovedAt())
                .refundedAt(payment.getRefundedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> findByTenantPaginated(
            UUID tenantId,
            PaymentStatus status,
            Pageable pageable
    ) {
        Page<Payment> page = status != null
                ? paymentRepository.findAllByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status, pageable)
                : paymentRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId, pageable);

        return PageResponse.from(page.map(paymentMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse findByOrderIdAndTenant(UUID orderId, UUID tenantId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .filter(p -> p.getTenant() != null && tenantId.equals(p.getTenant().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró un pago para la orden: " + orderId));
        return paymentMapper.toResponse(payment);
    }

    private MpOrderRequest buildMpOrderRequest(
            UUID paymentId,
            String slug,
            String customerEmail,
            OrderDraft draft
    ) {
        List<MpItem> mpItems = new ArrayList<>();
        if (draft.items() != null) {
            for (OrderDraftItem item : draft.items()) {
                BigDecimal lineTotal = item.lineTotalWithTax().setScale(2, RoundingMode.HALF_UP);
                String title = item.quantity() + "x " + item.productName();
                mpItems.add(
                        MpItem.builder()
                                .title(title)
                                .description(item.notes())
                                .unitPrice(lineTotal.toPlainString())
                                .quantity(1)
                                .build()
                );
            }
        }

        if (draft.deliveryFee() != null && draft.deliveryFee().compareTo(BigDecimal.ZERO) > 0) {
            mpItems.add(
                    MpItem.builder()
                            .title("Costo de envío")
                            .unitPrice(draft.deliveryFee().setScale(2, RoundingMode.HALF_UP).toPlainString())
                            .quantity(1)
                            .build()
            );
        }

        String paymentIdStr = paymentId.toString();
        MpBackUrls backUrls = MpBackUrls.builder()
                .success(frontendProperties.buildOrderResultUrl(slug, paymentIdStr, "success"))
                .pending(frontendProperties.buildOrderResultUrl(slug, paymentIdStr, "pending"))
                .failure(frontendProperties.buildOrderResultUrl(slug, paymentIdStr, "failure"))
                .build();

        return MpOrderRequest.builder()
                .type("online")
                .processingMode("manual")
                .totalAmount(draft.total().setScale(2, RoundingMode.HALF_UP).toPlainString())
                .externalReference(paymentIdStr)
                .expirationTime(properties.getOrderExpiration() != null ? properties.getOrderExpiration() : "PT30M")
                .payer(MpPayer.builder().email(customerEmail).build())
                .items(mpItems)
                .config(MpConfig.builder()
                        .online(MpOnlineConfig.builder()
                                .backUrls(backUrls)
                                .autoReturn("approved")
                                .build())
                        .build())
                .build();
    }

    private void cancelPreviousPendingAttempts(Tenant tenant, UUID customerUserId, String accessToken) {
        List<Payment> pendingPayments = paymentRepository.findAllByTenantIdAndCustomerIdAndStatusIn(
                tenant.getId(),
                customerUserId,
                List.of(PaymentStatus.PENDING)
        );

        for (Payment prev : pendingPayments) {
            if (prev.getMpOrderId() != null && !prev.getMpOrderId().isBlank()) {
                try {
                    mercadoPagoClient.cancelOrder(accessToken, "cancel-" + prev.getId(), prev.getMpOrderId());
                } catch (Exception ex) {
                    log.debug("No se pudo cancelar en MP el intento previo {}: {}", prev.getMpOrderId(), ex.getMessage());
                }
            }
            prev.setStatus(PaymentStatus.CANCELED);
            prev.setStatusDetail("replaced_by_new_checkout");
            paymentRepository.save(prev);
        }
    }

    private void clearCustomerCartAfterApprovedPayment(UUID tenantId, UUID customerUserId) {
        if (tenantId == null || customerUserId == null || cartRepository == null) {
            return;
        }
        cartRepository.findByTenantIdAndCustomerUserId(tenantId, customerUserId).ifPresent(cart -> {
            if (cart.getId() != null && cartItemRepository != null) {
                cartItemRepository.deleteAllByCartId(cart.getId());
            }
            cart.clear();
            cartRepository.save(cart);
        });
    }

    private Duration parseOrderExpirationDuration() {
        try {
            if (properties.getOrderExpiration() != null && !properties.getOrderExpiration().isBlank()) {
                return Duration.parse(properties.getOrderExpiration());
            }
        } catch (Exception ignored) {
        }
        return Duration.ofMinutes(30);
    }
}
