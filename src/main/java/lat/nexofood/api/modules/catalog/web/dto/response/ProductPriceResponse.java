package lat.nexofood.api.modules.catalog.web.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record ProductPriceResponse(
        UUID id,
        UUID productId,
        String name,
        BigDecimal price,
        Boolean isBase,
        BigDecimal discountPercentage,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime startTime,
        LocalTime endTime,
        String daysOfWeek,
        Integer priority,
        Boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
