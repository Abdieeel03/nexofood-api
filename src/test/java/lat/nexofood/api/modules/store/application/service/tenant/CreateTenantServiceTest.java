package lat.nexofood.api.modules.store.application.service.tenant;

import lat.nexofood.api.common.exception.ForbiddenException;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantMemberRepository;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.store.web.dto.request.TenantCreateRequest;
import lat.nexofood.api.modules.store.web.dto.response.TenantResponse;
import lat.nexofood.api.modules.store.web.mapper.TenantMapper;
import lat.nexofood.api.modules.subscription.domain.Subscription;
import lat.nexofood.api.modules.subscription.domain.SubscriptionStatus;
import lat.nexofood.api.modules.subscription.infrastructure.repository.SubscriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateTenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantMemberRepository tenantMemberRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private TenantMapper tenantMapper;

    @InjectMocks
    private CreateTenantService createTenantService;

    @Test
    @DisplayName("Lanza ForbiddenException cuando la suscripción pertenece a otro usuario")
    void throwsForbiddenWhenSubscriptionBelongsToAnotherUser() {
        UUID currentUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        User currentUser = User.builder().id(currentUserId).email("me@nexofood.lat").build();
        User otherUser = User.builder().id(otherUserId).email("other@nexofood.lat").build();

        Subscription subscription = Subscription.builder()
                .id(subscriptionId)
                .user(otherUser)
                .status(SubscriptionStatus.ACTIVE)
                .build();

        TenantCreateRequest request = TenantCreateRequest.builder()
                .subscriptionId(subscriptionId)
                .name("Mi Restaurante")
                .slug("mi-restaurante")
                .build();

        when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));

        assertThrows(ForbiddenException.class, () -> createTenantService.execute(request));
        verify(tenantRepository, never()).save(any());
    }

    @Test
    @DisplayName("Crea el restaurante usando automáticamente la suscripción vigente del usuario autenticado")
    void createsTenantResolvingActiveSubscriptionAutomatically() {
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        User currentUser = User.builder().id(userId).email("owner@nexofood.lat").build();
        Subscription subscription = Subscription.builder()
                .id(subscriptionId)
                .user(currentUser)
                .status(SubscriptionStatus.TRIAL)
                .endDate(OffsetDateTime.now().plusDays(15))
                .build();

        TenantCreateRequest request = TenantCreateRequest.builder()
                .name("Pollería Nexo")
                .slug("polleria-nexo")
                .build();

        Tenant tenantEntity = Tenant.builder().id(tenantId).owner(currentUser).subscription(subscription).build();
        TenantResponse expectedResponse = TenantResponse.builder().id(tenantId).ownerId(userId).slug("polleria-nexo").build();

        when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
        when(subscriptionRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(eq(userId), any()))
                .thenReturn(Optional.of(subscription));
        when(tenantRepository.findBySubscriptionId(subscriptionId)).thenReturn(Optional.empty());
        when(tenantRepository.existsBySlug("polleria-nexo")).thenReturn(false);
        when(tenantRepository.existsByOwnerId(userId)).thenReturn(false);
        when(tenantMapper.toEntity(request, subscription, currentUser)).thenReturn(tenantEntity);
        when(tenantRepository.save(tenantEntity)).thenReturn(tenantEntity);
        when(tenantMapper.toResponse(tenantEntity)).thenReturn(expectedResponse);

        TenantResponse result = createTenantService.execute(request);

        assertNotNull(result);
        assertEquals(userId, result.ownerId());
        verify(tenantMemberRepository).save(any());
    }

    @Test
    @DisplayName("Lanza ResourceNotFoundException cuando el usuario no tiene suscripción vigente")
    void throwsNotFoundWhenUserHasNoActiveSubscription() {
        UUID userId = UUID.randomUUID();
        User currentUser = User.builder().id(userId).build();

        TenantCreateRequest request = TenantCreateRequest.builder()
                .name("Pollería Nexo")
                .slug("polleria-nexo")
                .build();

        when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
        when(subscriptionRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(eq(userId), any()))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> createTenantService.execute(request));
    }

    @Test
    @DisplayName("Lanza ResourceConflictException cuando la suscripción ya tiene restaurante vinculado")
    void throwsConflictWhenSubscriptionAlreadyLinked() {
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        User currentUser = User.builder().id(userId).build();
        Subscription subscription = Subscription.builder()
                .id(subscriptionId)
                .user(currentUser)
                .status(SubscriptionStatus.ACTIVE)
                .endDate(OffsetDateTime.now().plusDays(10))
                .build();

        TenantCreateRequest request = TenantCreateRequest.builder()
                .subscriptionId(subscriptionId)
                .name("Pollería Nexo")
                .slug("polleria-nexo")
                .build();

        when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(tenantRepository.findBySubscriptionId(subscriptionId)).thenReturn(Optional.of(new Tenant()));

        assertThrows(ResourceConflictException.class, () -> createTenantService.execute(request));
    }
}
