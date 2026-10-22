package lat.nexofood.api.modules.subscription.application.service.plan;

import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.subscription.domain.SubscriptionPlan;
import lat.nexofood.api.modules.subscription.infrastructure.repository.SubscriptionPlanRepository;
import lat.nexofood.api.modules.subscription.web.dto.request.SubscriptionPlanRequest;
import lat.nexofood.api.modules.subscription.web.dto.response.SubscriptionPlanResponse;
import lat.nexofood.api.modules.subscription.web.mapper.SubscriptionPlanMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionPlanServiceImpl implements SubscriptionPlanService {

    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionPlanMapper planMapper;

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponse> getActivePlans() {
        return planRepository.findAllByIsActiveTrue()
                .stream()
                .map(planMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponse> getAllPlans() {
        return planRepository.findAll()
                .stream()
                .map(planMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionPlanResponse getPlanById(UUID planId) {
        SubscriptionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan de suscripción no encontrado"));
        return planMapper.toResponse(plan);
    }

    @Override
    @Transactional
    public SubscriptionPlanResponse createPlan(SubscriptionPlanRequest request) {
        String normalizedName = request.name().trim();
        if (planRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new ResourceConflictException("Ya existe un plan de suscripción con ese nombre");
        }

        SubscriptionPlan plan = planMapper.toEntity(request);
        plan.setName(normalizedName);

        SubscriptionPlan saved = planRepository.save(plan);
        log.info("Plan de suscripción creado con ID: {} ({})", saved.getId(), saved.getName());
        return planMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public SubscriptionPlanResponse updatePlan(UUID planId, SubscriptionPlanRequest request) {
        SubscriptionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan de suscripción no encontrado"));

        String normalizedName = request.name().trim();
        if (planRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, planId)) {
            throw new ResourceConflictException("Ya existe otro plan de suscripción con ese nombre");
        }

        plan.setName(normalizedName);
        plan.setPrice(request.price());
        plan.setBillingCycleDays(request.billingCycleDays() != null ? request.billingCycleDays() : 30);
        plan.setMaxProducts(request.maxProducts());
        if (request.isActive() != null) {
            plan.setIsActive(request.isActive());
        }

        SubscriptionPlan saved = planRepository.save(plan);
        log.info("Plan de suscripción actualizado con ID: {}", saved.getId());
        return planMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public SubscriptionPlanResponse changeStatus(UUID planId, Boolean isActive) {
        SubscriptionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan de suscripción no encontrado"));

        plan.setIsActive(isActive);
        SubscriptionPlan saved = planRepository.save(plan);
        log.info("Estado del plan de suscripción {} actualizado a isActive={}", planId, isActive);
        return planMapper.toResponse(saved);
    }
}
