package lat.nexofood.api.modules.store.application.service.customer;

import lat.nexofood.api.common.audit.AuditAction;
import lat.nexofood.api.common.audit.SecurityAuditLogger;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.store.application.usecase.customer.BlockTenantCustomerUseCase;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import lat.nexofood.api.modules.store.domain.TenantCustomerId;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantCustomerRepository;
import lat.nexofood.api.modules.store.web.dto.response.TenantCustomerResponse;
import lat.nexofood.api.modules.store.web.mapper.TenantCustomerMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
public class BlockTenantCustomerService implements BlockTenantCustomerUseCase {

    private final TenantCustomerRepository tenantCustomerRepository;
    private final TenantCustomerMapper tenantCustomerMapper;
    private final SecurityAuditLogger securityAuditLogger;

    public BlockTenantCustomerService(
            TenantCustomerRepository tenantCustomerRepository,
            TenantCustomerMapper tenantCustomerMapper
    ) {
        this(tenantCustomerRepository, tenantCustomerMapper, new SecurityAuditLogger());
    }

    @Autowired
    public BlockTenantCustomerService(
            TenantCustomerRepository tenantCustomerRepository,
            TenantCustomerMapper tenantCustomerMapper,
            SecurityAuditLogger securityAuditLogger
    ) {
        this.tenantCustomerRepository = tenantCustomerRepository;
        this.tenantCustomerMapper = tenantCustomerMapper;
        this.securityAuditLogger = securityAuditLogger != null ? securityAuditLogger : new SecurityAuditLogger();
    }

    @Override
    @Transactional
    public TenantCustomerResponse execute(UUID tenantId, UUID customerId, boolean isBlocked) {
        TenantCustomer customer = tenantCustomerRepository.findById(new TenantCustomerId(tenantId, customerId))
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado en este restaurante"));

        customer.setIsBlocked(isBlocked);
        TenantCustomer updated = tenantCustomerRepository.save(customer);
        securityAuditLogger.logEvent(
                isBlocked ? AuditAction.CUSTOMER_BLOCK : AuditAction.CUSTOMER_UNBLOCK,
                tenantId,
                customerId.toString(),
                isBlocked ? "Cliente bloqueado en el restaurante" : "Cliente desbloqueado en el restaurante"
        );
        log.info("Cliente {} {} en tenant {}", customerId, isBlocked ? "bloqueado" : "desbloqueado", tenantId);
        return tenantCustomerMapper.toResponse(updated);
    }
}
