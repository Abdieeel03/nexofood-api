package lat.nexofood.api.modules.store.application.service.customer;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ForbiddenException;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import lat.nexofood.api.modules.store.domain.TenantCustomerId;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantCustomerRepository;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurrentCustomerResolverTest {

    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private TenantCustomerRepository tenantCustomerRepository;

    @InjectMocks
    private CurrentCustomerResolver currentCustomerResolver;

    private User user;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(UUID.randomUUID())
                .email("cliente@nexofood.lat")
                .fullName("Carlos Cliente")
                .isActive(true)
                .build();

        tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .name("Pizzería Don Mario")
                .slug("pizzeria-don-mario")
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("resolveBySlug: autoasocia al usuario autenticado si aún no es cliente de la tienda")
    void resolveBySlugAutoCreatesCustomerWhenNotFound() {
        TenantCustomerId expectedId = new TenantCustomerId(tenant.getId(), user.getId());
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(tenantRepository.findBySlug("pizzeria-don-mario")).thenReturn(Optional.of(tenant));
        when(tenantCustomerRepository.findById(expectedId)).thenReturn(Optional.empty());
        when(tenantCustomerRepository.save(any(TenantCustomer.class))).thenAnswer(inv -> inv.getArgument(0));

        TenantCustomer resolved = currentCustomerResolver.resolveBySlug("pizzeria-don-mario");

        assertNotNull(resolved);
        assertEquals(tenant.getId(), resolved.getId().getTenantId());
        assertEquals(user.getId(), resolved.getId().getUserId());
        assertFalse(resolved.getIsBlocked());
        verify(tenantCustomerRepository).save(any(TenantCustomer.class));
    }

    @Test
    @DisplayName("resolveBySlug: lanza ForbiddenException (403) si el cliente está bloqueado en la tienda")
    void resolveBySlugRejectsBlockedCustomer() {
        TenantCustomerId id = new TenantCustomerId(tenant.getId(), user.getId());
        TenantCustomer blockedCustomer = TenantCustomer.builder()
                .id(id)
                .tenant(tenant)
                .user(user)
                .isBlocked(true)
                .build();

        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(tenantRepository.findBySlug("pizzeria-don-mario")).thenReturn(Optional.of(tenant));
        when(tenantCustomerRepository.findById(id)).thenReturn(Optional.of(blockedCustomer));

        assertThrows(ForbiddenException.class, () ->
                currentCustomerResolver.resolveBySlug("pizzeria-don-mario")
        );
    }

    @Test
    @DisplayName("resolveBySlug: lanza BadRequestException (400) si el restaurante está inactivo")
    void resolveBySlugRejectsInactiveTenant() {
        tenant.setIsActive(false);
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(tenantRepository.findBySlug("pizzeria-don-mario")).thenReturn(Optional.of(tenant));

        assertThrows(BadRequestException.class, () ->
                currentCustomerResolver.resolveBySlug("pizzeria-don-mario")
        );
    }
}
