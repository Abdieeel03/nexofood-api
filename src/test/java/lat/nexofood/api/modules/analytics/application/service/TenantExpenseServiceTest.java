package lat.nexofood.api.modules.analytics.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.modules.analytics.domain.ExpenseCategory;
import lat.nexofood.api.modules.analytics.domain.ExpenseType;
import lat.nexofood.api.modules.analytics.domain.TenantExpense;
import lat.nexofood.api.modules.analytics.infrastructure.repository.TenantExpenseRepository;
import lat.nexofood.api.modules.analytics.web.dto.request.TenantExpenseRequest;
import lat.nexofood.api.modules.analytics.web.dto.response.TenantExpenseResponse;
import lat.nexofood.api.modules.analytics.web.mapper.TenantExpenseMapper;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantExpenseServiceTest {

    @Mock
    private TenantExpenseRepository expenseRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Spy
    private TenantExpenseMapper expenseMapper = new TenantExpenseMapper();

    @InjectMocks
    private TenantExpenseServiceImpl expenseService;

    @Test
    @DisplayName("Debe crear un gasto para el tenant con escala monetaria de 2 decimales")
    void shouldCreateExpense() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().id(tenantId).name("Mi Tienda").build();
        User user = User.builder().id(userId).email("owner@test.com").build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(expenseRepository.save(any(TenantExpense.class))).thenAnswer(inv -> {
            TenantExpense e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        TenantExpenseRequest request = TenantExpenseRequest.builder()
                .type(ExpenseType.OPERATING)
                .category(ExpenseCategory.RENT)
                .description("  Alquiler mensual  ")
                .amount(new BigDecimal("1500.5"))
                .expenseDate(LocalDate.of(2026, 11, 1))
                .notes("Local principal")
                .build();

        TenantExpenseResponse response = expenseService.create(tenantId, request);

        assertThat(response.id()).isNotNull();
        assertThat(response.tenantId()).isEqualTo(tenantId);
        assertThat(response.createdBy()).isEqualTo(userId);
        assertThat(response.description()).isEqualTo("Alquiler mensual");
        assertThat(response.amount()).isEqualByComparingTo("1500.50");
        assertThat(response.type()).isEqualTo(ExpenseType.OPERATING);
        assertThat(response.category()).isEqualTo(ExpenseCategory.RENT);
    }

    @Test
    @DisplayName("Debe rechazar rango de fechas inválido al listar gastos")
    void shouldRejectInvalidDateRange() {
        UUID tenantId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 11, 20);
        LocalDate to = LocalDate.of(2026, 11, 10);

        assertThatThrownBy(() -> expenseService.findAll(tenantId, from, to, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Debe eliminar un gasto existente del tenant")
    void shouldDeleteExpense() {
        UUID tenantId = UUID.randomUUID();
        UUID expenseId = UUID.randomUUID();
        TenantExpense expense = TenantExpense.builder().id(expenseId).build();

        when(expenseRepository.findByIdAndTenantId(expenseId, tenantId)).thenReturn(Optional.of(expense));

        expenseService.delete(tenantId, expenseId);

        verify(expenseRepository).delete(expense);
    }

    @Test
    @DisplayName("Debe lanzar ResourceNotFoundException si el gasto no pertenece al tenant")
    void shouldThrowWhenExpenseNotFound() {
        UUID tenantId = UUID.randomUUID();
        UUID expenseId = UUID.randomUUID();
        when(expenseRepository.findByIdAndTenantId(expenseId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseService.findById(tenantId, expenseId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
