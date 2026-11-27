package lat.nexofood.api.modules.analytics.application.service;

import jakarta.persistence.criteria.Predicate;
import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.response.PageResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantExpenseServiceImpl implements TenantExpenseService {

    private final TenantExpenseRepository expenseRepository;
    private final TenantRepository tenantRepository;
    private final TenantExpenseMapper expenseMapper;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TenantExpenseResponse> findAll(
            UUID tenantId,
            LocalDate fromDate,
            LocalDate toDate,
            ExpenseType type,
            ExpenseCategory category,
            Pageable pageable
    ) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BadRequestException("La fecha de inicio (fromDate) no puede ser posterior a la fecha fin (toDate).");
        }

        Specification<TenantExpense> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenant").get("id"), tenantId));
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("expenseDate"), toDate));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Pageable effectivePageable = pageable != null && pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(
                        pageable != null ? pageable.getPageNumber() : 0,
                        pageable != null ? pageable.getPageSize() : 20,
                        Sort.by(Sort.Order.desc("expenseDate"), Sort.Order.desc("createdAt"))
                );

        Page<TenantExpenseResponse> page = expenseRepository.findAll(spec, effectivePageable)
                .map(expenseMapper::toResponse);
        return PageResponse.from(page);
    }

    @Override
    @Transactional(readOnly = true)
    public TenantExpenseResponse findById(UUID tenantId, UUID expenseId) {
        TenantExpense expense = expenseRepository.findByIdAndTenantId(expenseId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Gasto no encontrado con ID: " + expenseId));
        return expenseMapper.toResponse(expense);
    }

    @Override
    @Transactional
    public TenantExpenseResponse create(UUID tenantId, TenantExpenseRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado con ID: " + tenantId));

        User createdBy = resolveCurrentUserOrNull();
        TenantExpense entity = expenseMapper.toEntity(request, tenant, createdBy);
        TenantExpense saved = expenseRepository.save(entity);
        return expenseMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public TenantExpenseResponse update(UUID tenantId, UUID expenseId, TenantExpenseRequest request) {
        TenantExpense expense = expenseRepository.findByIdAndTenantId(expenseId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Gasto no encontrado con ID: " + expenseId));

        expense.setType(request.type());
        expense.setCategory(request.category());
        expense.setDescription(request.description() != null ? request.description().trim() : null);
        expense.setAmount(request.amount().setScale(2, RoundingMode.HALF_UP));
        expense.setExpenseDate(request.expenseDate());
        expense.setNotes(request.notes());

        TenantExpense updated = expenseRepository.save(expense);
        return expenseMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void delete(UUID tenantId, UUID expenseId) {
        TenantExpense expense = expenseRepository.findByIdAndTenantId(expenseId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Gasto no encontrado con ID: " + expenseId));
        expenseRepository.delete(expense);
    }

    private User resolveCurrentUserOrNull() {
        if (currentUserProvider == null) {
            return null;
        }
        try {
            return currentUserProvider.getCurrentUser();
        } catch (Exception ignored) {
            return null;
        }
    }
}
