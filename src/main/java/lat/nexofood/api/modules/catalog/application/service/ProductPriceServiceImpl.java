package lat.nexofood.api.modules.catalog.application.service;

import lat.nexofood.api.common.audit.AuditAction;
import lat.nexofood.api.common.audit.SecurityAuditLogger;
import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.domain.ProductPrice;
import lat.nexofood.api.modules.catalog.infrastructure.repository.ProductPriceRepository;
import lat.nexofood.api.modules.catalog.infrastructure.repository.ProductRepository;
import lat.nexofood.api.modules.catalog.web.dto.request.ProductPriceRequest;
import lat.nexofood.api.modules.catalog.web.dto.response.ProductPriceResponse;
import lat.nexofood.api.modules.catalog.web.mapper.ProductPriceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProductPriceServiceImpl implements ProductPriceService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

    private final ProductPriceRepository productPriceRepository;
    private final ProductRepository productRepository;
    private final ProductPriceMapper productPriceMapper;
    private final SecurityAuditLogger securityAuditLogger;

    public ProductPriceServiceImpl(
            ProductPriceRepository productPriceRepository,
            ProductRepository productRepository,
            ProductPriceMapper productPriceMapper
    ) {
        this(productPriceRepository, productRepository, productPriceMapper, new SecurityAuditLogger());
    }

    @Autowired
    public ProductPriceServiceImpl(
            ProductPriceRepository productPriceRepository,
            ProductRepository productRepository,
            ProductPriceMapper productPriceMapper,
            SecurityAuditLogger securityAuditLogger
    ) {
        this.productPriceRepository = productPriceRepository;
        this.productRepository = productRepository;
        this.productPriceMapper = productPriceMapper;
        this.securityAuditLogger = securityAuditLogger != null ? securityAuditLogger : new SecurityAuditLogger();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductPriceResponse> findAllByProduct(UUID tenantId, UUID productId) {
        Product product = getProductOrThrow(tenantId, productId);
        return productPriceRepository.findAllByProductId(product.getId())
                .stream()
                .map(productPriceMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductPriceResponse findById(UUID tenantId, UUID productId, UUID priceId) {
        Product product = getProductOrThrow(tenantId, productId);
        ProductPrice price = getPriceOrThrow(product.getId(), priceId);
        return productPriceMapper.toResponse(price);
    }

    @Override
    @Transactional
    public ProductPriceResponse create(UUID tenantId, UUID productId, ProductPriceRequest request) {
        Product product = getProductOrThrow(tenantId, productId);
        validatePriceRuleFields(request);

        List<ProductPrice> existingPrices = productPriceRepository.findAllByProductId(product.getId());

        boolean isBase = Boolean.TRUE.equals(request.isBase());
        boolean isActive = request.isActive() == null || Boolean.TRUE.equals(request.isActive());

        long activeBaseCount = existingPrices.stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsBase()) && Boolean.TRUE.equals(p.getIsActive()))
                .count();

        if (isBase && isActive && activeBaseCount > 0) {
            throw new ResourceConflictException(
                    "El producto ya cuenta con un precio base activo. Actualice el precio base existente en lugar de crear otro."
            );
        }

        if (!(isBase && isActive) && activeBaseCount == 0) {
            throw new BadRequestException("El producto debe contar con exactamente un precio base activo.");
        }

        ProductPrice candidate = productPriceMapper.toEntity(request, product);
        candidate.setId(null);

        if (!isBase && isActive) {
            validateNoAmbiguousOverlap(candidate, existingPrices, null);
        }

        ProductPrice saved = productPriceRepository.save(candidate);
        if (product.getPrices() != null && !product.getPrices().contains(saved)) {
            product.getPrices().add(saved);
        }
        securityAuditLogger.logEvent(
                AuditAction.PRICE_CHANGE,
                tenantId,
                productId.toString(),
                "Creación de regla de precio '" + saved.getName() + "' con monto=" + saved.getPrice()
        );
        return productPriceMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ProductPriceResponse update(UUID tenantId, UUID productId, UUID priceId, ProductPriceRequest request) {
        Product product = getProductOrThrow(tenantId, productId);
        ProductPrice existingPrice = getPriceOrThrow(product.getId(), priceId);
        validatePriceRuleFields(request);

        List<ProductPrice> allPrices = productPriceRepository.findAllByProductId(product.getId());

        boolean newIsBase = request.isBase() != null ? request.isBase() : Boolean.TRUE.equals(existingPrice.getIsBase());
        boolean newIsActive = request.isActive() != null ? request.isActive() : Boolean.TRUE.equals(existingPrice.getIsActive());

        long otherActiveBaseCount = allPrices.stream()
                .filter(p -> !p.getId().equals(priceId))
                .filter(p -> Boolean.TRUE.equals(p.getIsBase()) && Boolean.TRUE.equals(p.getIsActive()))
                .count();

        if (newIsBase && newIsActive && otherActiveBaseCount > 0) {
            throw new ResourceConflictException("El producto ya cuenta con otro precio base activo.");
        }

        if (!(newIsBase && newIsActive) && otherActiveBaseCount == 0) {
            throw new BadRequestException(
                    "El producto debe mantener exactamente un precio base activo. No se puede desactivar ni quitar la condición de precio base."
            );
        }

        BigDecimal previousAmount = existingPrice.getPrice();
        existingPrice.setName(request.name());
        existingPrice.setPrice(request.price());
        existingPrice.setIsBase(newIsBase);
        existingPrice.setDiscountPercentage(request.discountPercentage());
        existingPrice.setStartDate(request.startDate());
        existingPrice.setEndDate(request.endDate());
        existingPrice.setStartTime(request.startTime());
        existingPrice.setEndTime(request.endTime());
        existingPrice.setDaysOfWeek(request.daysOfWeek());
        existingPrice.setPriority(request.priority() != null ? request.priority() : existingPrice.getPriority());
        existingPrice.setIsActive(newIsActive);

        if (!newIsBase && newIsActive) {
            validateNoAmbiguousOverlap(existingPrice, allPrices, priceId);
        }

        ProductPrice updated = productPriceRepository.save(existingPrice);
        securityAuditLogger.logEvent(
                AuditAction.PRICE_CHANGE,
                tenantId,
                priceId.toString(),
                "Actualización de precio en producto " + productId + " de " + previousAmount + " a " + updated.getPrice()
        );
        return productPriceMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void delete(UUID tenantId, UUID productId, UUID priceId) {
        Product product = getProductOrThrow(tenantId, productId);
        ProductPrice price = getPriceOrThrow(product.getId(), priceId);

        if (Boolean.TRUE.equals(price.getIsBase()) && Boolean.TRUE.equals(price.getIsActive())) {
            throw new BadRequestException(
                    "No se puede eliminar el precio base activo del producto. Edite su valor en su lugar."
            );
        }

        if (product.getPrices() != null) {
            product.getPrices().remove(price);
        }
        productPriceRepository.delete(price);
        securityAuditLogger.logEvent(
                AuditAction.PRICE_CHANGE,
                tenantId,
                priceId.toString(),
                "Eliminación de regla de precio '" + price.getName() + "' en producto " + productId
        );
    }

    @Override
    public void validatePriceRuleFields(ProductPriceRequest request) {
        if (request == null) {
            throw new BadRequestException("Los datos de la regla de precio son obligatorios.");
        }
        if (request.price() == null || request.price().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("El precio es obligatorio y no puede ser negativo.");
        }
        if (request.discountPercentage() != null) {
            if (request.discountPercentage().compareTo(BigDecimal.ZERO) < 0
                    || request.discountPercentage().compareTo(ONE_HUNDRED) > 0) {
                throw new BadRequestException("El porcentaje de descuento debe estar entre 0 y 100.");
            }
        }
        if (request.startDate() != null && request.endDate() != null
                && request.endDate().isBefore(request.startDate())) {
            throw new BadRequestException("La fecha de fin no puede ser anterior a la fecha de inicio.");
        }
        if (request.startTime() != null && request.endTime() != null
                && request.startTime().equals(request.endTime())) {
            throw new BadRequestException("La hora de inicio y la hora de fin no pueden ser iguales.");
        }
        if (request.daysOfWeek() != null) {
            for (Short day : request.daysOfWeek()) {
                if (day == null || day < 1 || day > 7) {
                    throw new BadRequestException(
                            "Los días de la semana deben estar en el rango ISO de 1 (Lunes) a 7 (Domingo)."
                    );
                }
            }
        }
    }

    private void validateNoAmbiguousOverlap(
            ProductPrice candidate,
            List<ProductPrice> existingPrices,
            UUID excludeId
    ) {
        int candidatePriority = candidate.getPriority() != null ? candidate.getPriority() : 0;

        for (ProductPrice existing : existingPrices) {
            if (excludeId != null && excludeId.equals(existing.getId())) {
                continue;
            }
            if (Boolean.TRUE.equals(existing.getIsBase()) || !Boolean.TRUE.equals(existing.getIsActive())) {
                continue;
            }

            int existingPriority = existing.getPriority() != null ? existing.getPriority() : 0;
            if (candidatePriority != existingPriority) {
                continue;
            }

            if (datesOverlap(candidate.getStartDate(), candidate.getEndDate(), existing.getStartDate(), existing.getEndDate())
                    && daysOverlap(candidate.getDaysOfWeek(), existing.getDaysOfWeek())
                    && timesOverlap(candidate.getStartTime(), candidate.getEndTime(), existing.getStartTime(), existing.getEndTime())) {
                throw new ResourceConflictException(
                        "Existe un solapamiento ambiguo de fechas/horarios con otra regla de precio activa de igual prioridad ('"
                                + existing.getName() + "'). Asigne una prioridad distinta o ajuste los rangos de vigencia."
                );
            }
        }
    }

    private boolean datesOverlap(LocalDate start1, LocalDate end1, LocalDate start2, LocalDate end2) {
        LocalDate effStart1 = start1 != null ? start1 : LocalDate.MIN;
        LocalDate effEnd1 = end1 != null ? end1 : LocalDate.MAX;
        LocalDate effStart2 = start2 != null ? start2 : LocalDate.MIN;
        LocalDate effEnd2 = end2 != null ? end2 : LocalDate.MAX;
        return !effStart1.isAfter(effEnd2) && !effStart2.isAfter(effEnd1);
    }

    private boolean daysOverlap(Short[] days1, Short[] days2) {
        if (days1 == null || days1.length == 0 || days2 == null || days2.length == 0) {
            return true;
        }
        Set<Short> set1 = Arrays.stream(days1).collect(Collectors.toSet());
        for (Short d : days2) {
            if (set1.contains(d)) {
                return true;
            }
        }
        return false;
    }

    private boolean timesOverlap(LocalTime start1, LocalTime end1, LocalTime start2, LocalTime end2) {
        List<TimeSegment> segments1 = toTimeSegments(start1, end1);
        List<TimeSegment> segments2 = toTimeSegments(start2, end2);

        for (TimeSegment seg1 : segments1) {
            for (TimeSegment seg2 : segments2) {
                if (!seg1.start().isAfter(seg2.end()) && !seg2.start().isAfter(seg1.end())) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<TimeSegment> toTimeSegments(LocalTime start, LocalTime end) {
        if (start == null && end == null) {
            return List.of(new TimeSegment(LocalTime.MIN, LocalTime.MAX));
        }
        if (start == null) {
            return List.of(new TimeSegment(LocalTime.MIN, end));
        }
        if (end == null) {
            return List.of(new TimeSegment(start, LocalTime.MAX));
        }
        if (!start.isAfter(end)) {
            return List.of(new TimeSegment(start, end));
        }
        // Horario nocturno que cruza medianoche
        return List.of(
                new TimeSegment(start, LocalTime.MAX),
                new TimeSegment(LocalTime.MIN, end)
        );
    }

    private record TimeSegment(LocalTime start, LocalTime end) {}

    private Product getProductOrThrow(UUID tenantId, UUID productId) {
        return productRepository.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + productId));
    }

    private ProductPrice getPriceOrThrow(UUID productId, UUID priceId) {
        return productPriceRepository.findByIdAndProductId(priceId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Regla de precio no encontrada con ID: " + priceId));
    }
}
