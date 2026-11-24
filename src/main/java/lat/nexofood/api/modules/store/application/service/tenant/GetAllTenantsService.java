package lat.nexofood.api.modules.store.application.service.tenant;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.store.application.usecase.tenant.GetAllTenantsUseCase;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.store.web.dto.response.TenantResponse;
import lat.nexofood.api.modules.store.web.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GetAllTenantsService implements GetAllTenantsUseCase {

    private final TenantRepository tenantRepository;
    private final TenantMapper tenantMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TenantResponse> execute(Boolean isActive, Boolean isOpen, String search, Pageable pageable) {
        LocalTime currentTime = LocalTime.now();

        Specification<Tenant> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (isActive != null) {
                predicates.add(cb.equal(root.get("isActive"), isActive));
            }

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("slug")), pattern)
                ));
            }

            if (isOpen != null) {
                Predicate effectivelyOpen = buildEffectivelyOpenPredicate(root, cb, currentTime);
                predicates.add(Boolean.TRUE.equals(isOpen) ? effectivelyOpen : cb.not(effectivelyOpen));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Tenant> page = tenantRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(tenantMapper::toResponse));
    }

    private Predicate buildEffectivelyOpenPredicate(
            jakarta.persistence.criteria.Root<Tenant> root,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            LocalTime currentTime
    ) {
        Expression<LocalTime> opening = root.get("openingTime");
        Expression<LocalTime> closing = root.get("closingTime");

        Predicate hasSchedule = cb.and(cb.isNotNull(opening), cb.isNotNull(closing));
        Predicate noSchedule = cb.or(cb.isNull(opening), cb.isNull(closing));

        Predicate alwaysOpenSchedule = cb.equal(opening, closing);
        Predicate sameDayOpen = cb.and(
                cb.lessThan(opening, closing),
                cb.lessThanOrEqualTo(opening, currentTime),
                cb.greaterThan(closing, currentTime)
        );
        Predicate overnightOpen = cb.and(
                cb.greaterThan(opening, closing),
                cb.or(
                        cb.lessThanOrEqualTo(opening, currentTime),
                        cb.greaterThan(closing, currentTime)
                )
        );

        Predicate withinSchedule = cb.and(
                hasSchedule,
                cb.or(alwaysOpenSchedule, sameDayOpen, overnightOpen)
        );
        Predicate openWithoutSchedule = cb.and(
                noSchedule,
                cb.or(cb.isTrue(root.get("isOpen")), cb.isNull(root.get("isOpen")))
        );

        return cb.and(
                cb.isTrue(root.get("isActive")),
                cb.or(withinSchedule, openWithoutSchedule)
        );
    }
}
