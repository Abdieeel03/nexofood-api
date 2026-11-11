package lat.nexofood.api.modules.subscription.application.service;

import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.FrontendProperties;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.MercadoPagoClient;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.MercadoPagoProperties;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpAuthorizedPaymentResponse;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpAuthorizedPaymentResponse.MpPaymentSummary;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpPreapprovalRequest;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpPreapprovalResponse;
import lat.nexofood.api.modules.payment.web.mapper.PaymentMapper;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.subscription.application.scheduler.SubscriptionExpirationScheduler;
import lat.nexofood.api.modules.subscription.application.service.subscription.SubscriptionBillingServiceImpl;
import lat.nexofood.api.modules.subscription.application.service.subscription.SubscriptionServiceImpl;
import lat.nexofood.api.modules.subscription.domain.Subscription;
import lat.nexofood.api.modules.subscription.domain.SubscriptionPayment;
import lat.nexofood.api.modules.subscription.domain.SubscriptionPlan;
import lat.nexofood.api.modules.subscription.domain.SubscriptionStatus;
import lat.nexofood.api.modules.subscription.infrastructure.repository.SubscriptionPaymentRepository;
import lat.nexofood.api.modules.subscription.infrastructure.repository.SubscriptionPlanRepository;
import lat.nexofood.api.modules.subscription.infrastructure.repository.SubscriptionRepository;
import lat.nexofood.api.modules.subscription.web.dto.response.SubscriptionCheckoutResponse;
import lat.nexofood.api.modules.subscription.web.dto.response.SubscriptionResponse;
import lat.nexofood.api.modules.subscription.web.mapper.SubscriptionMapper;
import lat.nexofood.api.modules.subscription.web.mapper.SubscriptionPlanMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionBillingServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private SubscriptionPlanRepository planRepository;
    @Mock
    private SubscriptionPaymentRepository subscriptionPaymentRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private MercadoPagoClient mercadoPagoClient;

    private SubscriptionBillingServiceImpl billingService;
    private SubscriptionServiceImpl subscriptionService;
    private SubscriptionExpirationScheduler expirationScheduler;

    private User owner;
    private SubscriptionPlan planPro;

    @BeforeEach
    void setUp() {
        MercadoPagoProperties properties = MercadoPagoProperties.builder().currency("PEN").build();
        FrontendProperties frontendProperties = new FrontendProperties();
        SubscriptionMapper subscriptionMapper = new SubscriptionMapper(new SubscriptionPlanMapper());

        billingService = new SubscriptionBillingServiceImpl(
                subscriptionRepository,
                planRepository,
                subscriptionPaymentRepository,
                tenantRepository,
                currentUserProvider,
                mercadoPagoClient,
                properties,
                frontendProperties,
                new PaymentMapper(),
                subscriptionMapper
        );

        subscriptionService = new SubscriptionServiceImpl(
                subscriptionRepository,
                planRepository,
                tenantRepository,
                currentUserProvider,
                subscriptionMapper,
                billingService
        );

        expirationScheduler = new SubscriptionExpirationScheduler(subscriptionRepository, tenantRepository);

        owner = User.builder()
                .id(UUID.randomUUID())
                .email("owner@nexofood.lat")
                .fullName("Propietario")
                .build();

        planPro = SubscriptionPlan.builder()
                .id(UUID.randomUUID())
                .name("Plan Pro")
                .price(new BigDecimal("49.90"))
                .billingCycleDays(30)
                .maxProducts(100)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("startCheckout: reutiliza la suscripción del tenant en TRIAL y difiere start_date al fin del TRIAL")
    void startCheckoutReusesTenantTrialSubscriptionAndDefersStartDate() {
        OffsetDateTime trialEnd = OffsetDateTime.now().plusDays(10);
        Subscription trialSub = Subscription.builder()
                .id(UUID.randomUUID())
                .user(owner)
                .plan(planPro)
                .status(SubscriptionStatus.TRIAL)
                .startDate(OffsetDateTime.now().minusDays(5))
                .endDate(trialEnd)
                .build();

        Tenant tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .subscription(trialSub)
                .isActive(true)
                .build();

        when(currentUserProvider.getCurrentUser()).thenReturn(owner);
        when(planRepository.findById(planPro.getId())).thenReturn(Optional.of(planPro));
        when(tenantRepository.findByOwnerId(owner.getId())).thenReturn(Optional.of(tenant));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<MpPreapprovalRequest> reqCaptor = ArgumentCaptor.forClass(MpPreapprovalRequest.class);
        when(mercadoPagoClient.createPreapproval(reqCaptor.capture())).thenReturn(
                MpPreapprovalResponse.builder()
                        .id("preapp-123")
                        .status("pending")
                        .initPoint("https://www.mercadopago.com.pe/subscriptions/checkout?preapproval_id=preapp-123")
                        .build()
        );

        SubscriptionCheckoutResponse response = billingService.startCheckout(planPro.getId());

        assertEquals(trialSub.getId(), response.subscriptionId());
        assertEquals("preapp-123", response.mpPreapprovalId());
        assertNotNull(reqCaptor.getValue().autoRecurring().startDate());
        assertEquals(1, reqCaptor.getValue().autoRecurring().frequency());
        assertEquals("months", reqCaptor.getValue().autoRecurring().frequencyType());
    }

    @Test
    @DisplayName("Webhooks de suscripción: authorized activa suscripción y cobro aprobado extiende endDate de forma idempotente")
    void preapprovalAndAuthorizedPaymentWebhooksActivateAndExtendSubscriptionIdempotently() {
        UUID subId = UUID.randomUUID();
        Subscription sub = Subscription.builder()
                .id(subId)
                .user(owner)
                .plan(planPro)
                .status(SubscriptionStatus.PAST_DUE)
                .mpPreapprovalId("preapp-123")
                .startDate(OffsetDateTime.now().minusDays(40))
                .endDate(OffsetDateTime.now().minusDays(10))
                .build();

        Tenant tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .subscription(sub)
                .isActive(false)
                .build();

        when(mercadoPagoClient.getPreapproval("preapp-123")).thenReturn(
                MpPreapprovalResponse.builder()
                        .id("preapp-123")
                        .status("authorized")
                        .externalReference(subId.toString())
                        .build()
        );
        when(subscriptionRepository.findByMpPreapprovalId("preapp-123")).thenReturn(Optional.of(sub));
        when(tenantRepository.findBySubscriptionId(subId)).thenReturn(Optional.of(tenant));

        billingService.processPreapprovalNotification("preapp-123");

        assertEquals(SubscriptionStatus.ACTIVE, sub.getStatus());
        assertTrue(tenant.getIsActive());
        assertTrue(sub.getEndDate().isAfter(OffsetDateTime.now().plusDays(25)));

        // Ahora llega un cobro recurrente autorizado
        when(mercadoPagoClient.getAuthorizedPayment("auth-pay-001")).thenReturn(
                MpAuthorizedPaymentResponse.builder()
                        .id("auth-pay-001")
                        .preapprovalId("preapp-123")
                        .status("processed")
                        .transactionAmount(new BigDecimal("49.90"))
                        .currencyId("PEN")
                        .payment(MpPaymentSummary.builder().id("pay-999").status("approved").build())
                        .build()
        );
        when(subscriptionPaymentRepository.findByMpAuthorizedPaymentId("auth-pay-001"))
                .thenReturn(Optional.empty());
        when(subscriptionPaymentRepository.save(any(SubscriptionPayment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        billingService.processAuthorizedPaymentNotification("auth-pay-001");
        verify(subscriptionPaymentRepository).save(any(SubscriptionPayment.class));
    }

    @Test
    @DisplayName("Cancelación de suscripción pagada (cancel-at-period-end): cancela recurrencia en MP pero mantiene restaurante activo hasta que venza endDate")
    void cancelPaidSubscriptionKeepsTenantActiveUntilPeriodEnds() {
        UUID subId = UUID.randomUUID();
        Subscription activePaidSub = Subscription.builder()
                .id(subId)
                .user(owner)
                .plan(planPro)
                .status(SubscriptionStatus.ACTIVE)
                .mpPreapprovalId("preapp-555")
                .mpPreapprovalStatus("authorized")
                .startDate(OffsetDateTime.now().minusDays(10))
                .endDate(OffsetDateTime.now().plusDays(20))
                .cancelAtPeriodEnd(false)
                .build();

        Tenant tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .subscription(activePaidSub)
                .isActive(true)
                .build();

        when(currentUserProvider.getCurrentUser()).thenReturn(owner);
        when(subscriptionRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.of(activePaidSub));
        when(mercadoPagoClient.updatePreapprovalStatus("preapp-555", "cancelled"))
                .thenReturn(MpPreapprovalResponse.builder().id("preapp-555").status("cancelled").build());
        when(subscriptionRepository.save(activePaidSub)).thenReturn(activePaidSub);

        SubscriptionResponse canceledResp = subscriptionService.cancel();

        // 1. Se mantiene ACTIVE con cancelAtPeriodEnd = true y el restaurante sigue activo
        assertEquals(SubscriptionStatus.ACTIVE, canceledResp.status());
        assertTrue(canceledResp.cancelAtPeriodEnd());
        assertTrue(tenant.getIsActive());
        verify(tenantRepository, never()).save(any());

        // 2. Cuando llega la fecha de fin de período, el scheduler la pasa a CANCELED y desactiva el restaurante
        activePaidSub.setEndDate(OffsetDateTime.now().minusMinutes(1));
        when(subscriptionRepository.findAllByStatusInAndEndDateBefore(any(), any()))
                .thenReturn(List.of(activePaidSub));
        when(tenantRepository.findBySubscriptionId(subId)).thenReturn(Optional.of(tenant));

        expirationScheduler.processExpiredSubscriptions();

        assertEquals(SubscriptionStatus.CANCELED, activePaidSub.getStatus());
        assertFalse(activePaidSub.getCancelAtPeriodEnd());
        assertFalse(tenant.getIsActive());
        verify(tenantRepository).save(tenant);
    }
}
