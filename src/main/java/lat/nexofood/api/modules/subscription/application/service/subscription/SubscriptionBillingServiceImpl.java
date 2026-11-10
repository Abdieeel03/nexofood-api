package lat.nexofood.api.modules.subscription.application.service.subscription;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.PaymentGatewayException;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.FrontendProperties;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.MercadoPagoClient;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.MercadoPagoProperties;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpAuthorizedPaymentResponse;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpPreapprovalRequest;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpPreapprovalRequest.MpAutoRecurring;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpPreapprovalResponse;
import lat.nexofood.api.modules.payment.web.mapper.PaymentMapper;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.subscription.domain.Subscription;
import lat.nexofood.api.modules.subscription.domain.SubscriptionPayment;
import lat.nexofood.api.modules.subscription.domain.SubscriptionPaymentStatus;
import lat.nexofood.api.modules.subscription.domain.SubscriptionPlan;
import lat.nexofood.api.modules.subscription.domain.SubscriptionStatus;
import lat.nexofood.api.modules.subscription.infrastructure.repository.SubscriptionPaymentRepository;
import lat.nexofood.api.modules.subscription.infrastructure.repository.SubscriptionPlanRepository;
import lat.nexofood.api.modules.subscription.infrastructure.repository.SubscriptionRepository;
import lat.nexofood.api.modules.subscription.web.dto.response.SubscriptionCheckoutResponse;
import lat.nexofood.api.modules.subscription.web.dto.response.SubscriptionPaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionBillingServiceImpl implements SubscriptionBillingService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionPaymentRepository subscriptionPaymentRepository;
    private final TenantRepository tenantRepository;
    private final CurrentUserProvider currentUserProvider;
    private final MercadoPagoClient mercadoPagoClient;
    private final MercadoPagoProperties properties;
    private final FrontendProperties frontendProperties;
    private final PaymentMapper paymentMapper;

    @Override
    @Transactional
    public SubscriptionCheckoutResponse startCheckout(UUID planId) {
        User user = currentUserProvider.getCurrentUser();

        SubscriptionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan de suscripción no encontrado"));

        if (!Boolean.TRUE.equals(plan.getIsActive())) {
            throw new BadRequestException("El plan de suscripción seleccionado no está disponible");
        }

        Subscription subscription = resolveOrCreateSubscriptionForCheckout(user, plan);

        // Si tenía un preapproval anterior en estado pending, intentamos cancelarlo antes de generar uno nuevo
        if (subscription.getMpPreapprovalId() != null
                && !subscription.getMpPreapprovalId().isBlank()
                && "pending".equalsIgnoreCase(subscription.getMpPreapprovalStatus())) {
            try {
                mercadoPagoClient.updatePreapprovalStatus(subscription.getMpPreapprovalId(), "cancelled");
            } catch (Exception ex) {
                log.debug("No se pudo cancelar preapproval previo {}: {}", subscription.getMpPreapprovalId(), ex.getMessage());
            }
        }

        int cycleDays = plan.getBillingCycleDays() != null && plan.getBillingCycleDays() > 0
                ? plan.getBillingCycleDays()
                : 30;

        int frequency;
        String frequencyType;
        if (cycleDays % 30 == 0) {
            frequency = cycleDays / 30;
            frequencyType = "months";
        } else {
            frequency = cycleDays;
            frequencyType = "days";
        }

        // Si el usuario está en TRIAL vigente, diferimos el primer cobro hasta el fin del TRIAL (P3)
        String startDateIso = null;
        OffsetDateTime now = OffsetDateTime.now();
        if (subscription.getStatus() == SubscriptionStatus.TRIAL
                && subscription.getEndDate() != null
                && subscription.getEndDate().isAfter(now)) {
            startDateIso = subscription.getEndDate().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }

        String currency = properties.getCurrency() != null && !properties.getCurrency().isBlank()
                ? properties.getCurrency()
                : "PEN";

        MpPreapprovalRequest mpRequest = MpPreapprovalRequest.builder()
                .reason("NexoFood - " + plan.getName())
                .externalReference(subscription.getId().toString())
                .payerEmail(user.getEmail())
                .backUrl(frontendProperties.buildSubscriptionResultUrl(subscription.getId().toString()))
                .status("pending")
                .autoRecurring(MpAutoRecurring.builder()
                        .frequency(frequency)
                        .frequencyType(frequencyType)
                        .transactionAmount(plan.getPrice())
                        .currencyId(currency)
                        .startDate(startDateIso)
                        .build())
                .build();

        MpPreapprovalResponse mpResponse = mercadoPagoClient.createPreapproval(mpRequest);
        if (mpResponse == null || mpResponse.id() == null || mpResponse.initPoint() == null) {
            throw new PaymentGatewayException("Mercado Pago no devolvió un enlace válido para la suscripción (init_point)");
        }

        subscription.setMpPreapprovalId(mpResponse.id());
        subscription.setMpPreapprovalStatus(mpResponse.status() != null ? mpResponse.status() : "pending");
        Subscription saved = subscriptionRepository.save(subscription);

        return SubscriptionCheckoutResponse.builder()
                .subscriptionId(saved.getId())
                .planId(plan.getId())
                .mpPreapprovalId(saved.getMpPreapprovalId())
                .initPoint(mpResponse.initPoint())
                .status(saved.getStatus())
                .build();
    }

    @Override
    @Transactional
    public void processPreapprovalNotification(String preapprovalId) {
        if (preapprovalId == null || preapprovalId.isBlank()) {
            return;
        }

        MpPreapprovalResponse mpPreapproval = mercadoPagoClient.getPreapproval(preapprovalId);
        if (mpPreapproval == null) {
            return;
        }

        Optional<Subscription> subscriptionOpt = findSubscriptionForPreapproval(
                preapprovalId,
                mpPreapproval.externalReference()
        );
        if (subscriptionOpt.isEmpty()) {
            log.debug("Notificación de preapproval ignorada: {} no corresponde a ninguna suscripción local", preapprovalId);
            return;
        }

        Subscription subscription = subscriptionOpt.get();
        subscription.setMpPreapprovalId(preapprovalId);
        String mpStatus = mpPreapproval.status() != null
                ? mpPreapproval.status().trim().toLowerCase(Locale.ROOT)
                : "";
        subscription.setMpPreapprovalStatus(mpStatus);

        OffsetDateTime now = OffsetDateTime.now();
        int cycleDays = subscription.getPlan() != null && subscription.getPlan().getBillingCycleDays() != null
                ? subscription.getPlan().getBillingCycleDays()
                : 30;

        switch (mpStatus) {
            case "authorized" -> {
                subscription.setStatus(SubscriptionStatus.ACTIVE);
                subscription.setCancelAtPeriodEnd(false);
                if (subscription.getEndDate() == null || subscription.getEndDate().isBefore(now)) {
                    subscription.setStartDate(now);
                    subscription.setEndDate(now.plusDays(cycleDays));
                }
                reactivateTenantIfLinked(subscription.getId());
            }
            case "paused" -> {
                if (subscription.getEndDate() != null && subscription.getEndDate().isBefore(now)) {
                    subscription.setStatus(SubscriptionStatus.PAST_DUE);
                    deactivateTenantIfLinked(subscription.getId());
                }
            }
            case "cancelled", "canceled" -> {
                // Si tiene período pagado vigente, mantenemos acceso abierto hasta endDate (cancel-at-period-end)
                if (subscription.getStatus() == SubscriptionStatus.ACTIVE
                        && subscription.getEndDate() != null
                        && subscription.getEndDate().isAfter(now)) {
                    subscription.setCancelAtPeriodEnd(true);
                } else {
                    subscription.setStatus(SubscriptionStatus.CANCELED);
                    deactivateTenantIfLinked(subscription.getId());
                }
            }
            default -> {
                // pending: mantiene su estado actual (TRIAL o PENDING)
            }
        }

        subscriptionRepository.save(subscription);
    }

    @Override
    @Transactional
    public void processAuthorizedPaymentNotification(String authorizedPaymentId) {
        if (authorizedPaymentId == null || authorizedPaymentId.isBlank()) {
            return;
        }

        MpAuthorizedPaymentResponse mpAuthPayment = mercadoPagoClient.getAuthorizedPayment(authorizedPaymentId);
        if (mpAuthPayment == null) {
            return;
        }

        Optional<Subscription> subscriptionOpt = findSubscriptionForPreapproval(
                mpAuthPayment.preapprovalId(),
                mpAuthPayment.externalReference()
        );
        if (subscriptionOpt.isEmpty()) {
            log.debug("Cobro recurrente {} ignorado: no se encontró suscripción asociada", authorizedPaymentId);
            return;
        }

        Subscription subscription = subscriptionOpt.get();
        SubscriptionPayment subPayment = subscriptionPaymentRepository
                .findByMpAuthorizedPaymentId(authorizedPaymentId)
                .orElseGet(() -> SubscriptionPayment.builder()
                        .subscription(subscription)
                        .mpAuthorizedPaymentId(authorizedPaymentId)
                        .build());

        boolean wasAlreadyApproved = subPayment.getId() != null
                && subPayment.getStatus() == SubscriptionPaymentStatus.APPROVED;

        SubscriptionPaymentStatus mappedPaymentStatus = mapAuthorizedPaymentStatus(mpAuthPayment.effectiveStatus());
        subPayment.setStatus(mappedPaymentStatus);
        subPayment.setMpPaymentId(mpAuthPayment.paymentId());
        subPayment.setAmount(mpAuthPayment.transactionAmount() != null
                ? mpAuthPayment.transactionAmount()
                : subscription.getPlan().getPrice());
        subPayment.setCurrency(mpAuthPayment.currencyId() != null ? mpAuthPayment.currencyId() : "PEN");
        if (mpAuthPayment.rawJson() != null) {
            subPayment.setRawResponse(paymentMapper.sanitizePaymentResponse(mpAuthPayment.rawJson()));
        }

        OffsetDateTime paidAt = parseDateOrNow(mpAuthPayment.dateCreated());
        int cycleDays = subscription.getPlan() != null && subscription.getPlan().getBillingCycleDays() != null
                ? subscription.getPlan().getBillingCycleDays()
                : 30;

        if (mappedPaymentStatus == SubscriptionPaymentStatus.APPROVED && !wasAlreadyApproved) {
            subPayment.setPaidAt(paidAt);
            OffsetDateTime candidateEnd = paidAt.plusDays(cycleDays);
            OffsetDateTime currentEnd = subscription.getEndDate();
            OffsetDateTime newEndDate = (currentEnd != null && currentEnd.isAfter(candidateEnd))
                    ? currentEnd
                    : candidateEnd;

            subPayment.setPeriodEnd(newEndDate);
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setCancelAtPeriodEnd(false);
            subscription.setEndDate(newEndDate);
            subscriptionRepository.save(subscription);
            reactivateTenantIfLinked(subscription.getId());
        }

        subscriptionPaymentRepository.save(subPayment);
    }

    @Override
    @Transactional
    public void cancelPreapproval(Subscription subscription) {
        if (subscription == null
                || subscription.getMpPreapprovalId() == null
                || subscription.getMpPreapprovalId().isBlank()) {
            return;
        }
        try {
            MpPreapprovalResponse resp = mercadoPagoClient.updatePreapprovalStatus(
                    subscription.getMpPreapprovalId(),
                    "cancelled"
            );
            subscription.setMpPreapprovalStatus(resp != null && resp.status() != null ? resp.status() : "cancelled");
        } catch (PaymentGatewayException ex) {
            log.warn("Error al cancelar preapproval {} en Mercado Pago: {}",
                    subscription.getMpPreapprovalId(), ex.getMessage());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionPaymentResponse> getMyPayments() {
        User user = currentUserProvider.getCurrentUser();
        Optional<Subscription> subOpt = subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId());
        if (subOpt.isEmpty()) {
            return List.of();
        }
        return subscriptionPaymentRepository
                .findAllBySubscriptionIdOrderByCreatedAtDesc(subOpt.get().getId())
                .stream()
                .map(this::toPaymentResponse)
                .toList();
    }

    private Subscription resolveOrCreateSubscriptionForCheckout(User user, SubscriptionPlan plan) {
        // 1. Si el usuario ya tiene un restaurante (relación 1:1 tenants.subscription_id), reutilizamos esa suscripción
        Optional<Tenant> existingTenantOpt = tenantRepository.findByOwnerId(user.getId());
        if (existingTenantOpt.isPresent() && existingTenantOpt.get().getSubscription() != null) {
            Subscription tenantSub = existingTenantOpt.get().getSubscription();
            if (tenantSub.getStatus() == SubscriptionStatus.ACTIVE
                    && !Boolean.TRUE.equals(tenantSub.getCancelAtPeriodEnd())
                    && "authorized".equalsIgnoreCase(tenantSub.getMpPreapprovalStatus())
                    && tenantSub.getEndDate() != null
                    && tenantSub.getEndDate().isAfter(OffsetDateTime.now())) {
                throw new ResourceConflictException("Ya cuentas con una suscripción activa y autorizada");
            }
            tenantSub.setPlan(plan);
            return subscriptionRepository.save(tenantSub);
        }

        // 2. Si no tiene tenant, buscamos su última suscripción
        Optional<Subscription> latestSubOpt = subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId());
        if (latestSubOpt.isPresent()) {
            Subscription latest = latestSubOpt.get();
            if (latest.getStatus() == SubscriptionStatus.ACTIVE
                    && !Boolean.TRUE.equals(latest.getCancelAtPeriodEnd())
                    && "authorized".equalsIgnoreCase(latest.getMpPreapprovalStatus())
                    && latest.getEndDate() != null
                    && latest.getEndDate().isAfter(OffsetDateTime.now())) {
                throw new ResourceConflictException("Ya cuentas con una suscripción activa y autorizada");
            }
            latest.setPlan(plan);
            if (latest.getStatus() != SubscriptionStatus.TRIAL && latest.getStatus() != SubscriptionStatus.ACTIVE) {
                latest.setStatus(SubscriptionStatus.PENDING);
            }
            return subscriptionRepository.save(latest);
        }

        // 3. Crear nueva suscripción en estado PENDING esperando autorización de Mercado Pago
        OffsetDateTime now = OffsetDateTime.now();
        int days = plan.getBillingCycleDays() != null ? plan.getBillingCycleDays() : 30;
        Subscription newSub = Subscription.builder()
                .plan(plan)
                .user(user)
                .status(SubscriptionStatus.PENDING)
                .startDate(now)
                .endDate(now.plusDays(days))
                .cancelAtPeriodEnd(false)
                .build();
        return subscriptionRepository.save(newSub);
    }

    private Optional<Subscription> findSubscriptionForPreapproval(String preapprovalId, String externalReference) {
        if (preapprovalId != null && !preapprovalId.isBlank()) {
            Optional<Subscription> byPreapproval = subscriptionRepository.findByMpPreapprovalId(preapprovalId);
            if (byPreapproval.isPresent()) {
                return byPreapproval;
            }
        }
        if (externalReference != null && !externalReference.isBlank()) {
            try {
                UUID subId = UUID.fromString(externalReference.trim());
                return subscriptionRepository.findById(subId);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Optional.empty();
    }

    private void reactivateTenantIfLinked(UUID subscriptionId) {
        tenantRepository.findBySubscriptionId(subscriptionId).ifPresent(tenant -> {
            if (Boolean.FALSE.equals(tenant.getIsActive())) {
                tenant.setIsActive(true);
                tenantRepository.save(tenant);
                log.info("Restaurante {} reactivado tras pago/autorización de suscripción {}",
                        tenant.getId(), subscriptionId);
            }
        });
    }

    private void deactivateTenantIfLinked(UUID subscriptionId) {
        tenantRepository.findBySubscriptionId(subscriptionId).ifPresent(tenant -> {
            if (Boolean.TRUE.equals(tenant.getIsActive())) {
                tenant.setIsActive(false);
                tenantRepository.save(tenant);
                log.info("Restaurante {} desactivado por estado de suscripción {}",
                        tenant.getId(), subscriptionId);
            }
        });
    }

    private SubscriptionPaymentStatus mapAuthorizedPaymentStatus(String rawStatus) {
        if (rawStatus == null) {
            return SubscriptionPaymentStatus.PENDING;
        }
        return switch (rawStatus.trim().toLowerCase(Locale.ROOT)) {
            case "approved", "processed" -> SubscriptionPaymentStatus.APPROVED;
            case "rejected", "cancelled", "canceled" -> SubscriptionPaymentStatus.REJECTED;
            case "refunded" -> SubscriptionPaymentStatus.REFUNDED;
            default -> SubscriptionPaymentStatus.PENDING;
        };
    }

    private OffsetDateTime parseDateOrNow(String isoDate) {
        if (isoDate != null && !isoDate.isBlank()) {
            try {
                return OffsetDateTime.parse(isoDate);
            } catch (Exception ignored) {
            }
        }
        return OffsetDateTime.now();
    }

    private SubscriptionPaymentResponse toPaymentResponse(SubscriptionPayment sp) {
        return SubscriptionPaymentResponse.builder()
                .id(sp.getId())
                .subscriptionId(sp.getSubscription() != null ? sp.getSubscription().getId() : null)
                .mpAuthorizedPaymentId(sp.getMpAuthorizedPaymentId())
                .mpPaymentId(sp.getMpPaymentId())
                .status(sp.getStatus())
                .amount(sp.getAmount())
                .currency(sp.getCurrency())
                .paidAt(sp.getPaidAt())
                .periodEnd(sp.getPeriodEnd())
                .createdAt(sp.getCreatedAt())
                .build();
    }
}
