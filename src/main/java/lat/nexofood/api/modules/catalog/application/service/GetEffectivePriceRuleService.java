package lat.nexofood.api.modules.catalog.application.service;

import lat.nexofood.api.modules.catalog.application.usecase.GetBasePriceUseCase;
import lat.nexofood.api.modules.catalog.application.usecase.GetEffectivePriceRuleUseCase;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.domain.ProductPrice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Comparator;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetEffectivePriceRuleService implements GetEffectivePriceRuleUseCase {

    private final GetBasePriceUseCase getBasePriceUseCase;

    @Override
    public ProductPrice execute(Product product) {
        return execute(product, LocalDateTime.now());
    }

    @Override
    public ProductPrice execute(Product product, LocalDateTime dateTime) {
        if (product == null || product.getPrices() == null || product.getPrices().isEmpty()) {
            return null;
        }

        return product.getPrices().stream()
                .filter(p -> isPriceApplicable(p, dateTime))
                .max(Comparator.comparingInt(p -> p.getPriority() != null ? p.getPriority() : 0))
                .orElseGet(() -> getBasePriceUseCase.executeRule(product));
    }

    @Override
    public boolean isPriceApplicable(ProductPrice price, LocalDateTime dateTime) {
        if (price == null || !Boolean.TRUE.equals(price.getIsActive())) {
            return false;
        }

        LocalDate targetDate = dateTime.toLocalDate();
        LocalTime targetTime = dateTime.toLocalTime();
        DayOfWeek targetDayOfWeek = dateTime.getDayOfWeek();

        // Validar rango de fechas
        if (price.getStartDate() != null && targetDate.isBefore(price.getStartDate())) {
            return false;
        }
        if (price.getEndDate() != null && targetDate.isAfter(price.getEndDate())) {
            return false;
        }

        // Validar días de la semana
        if (price.getDaysOfWeek() != null && !price.getDaysOfWeek().isBlank()) {
            boolean dayMatches = Arrays.stream(price.getDaysOfWeek().split(","))
                    .map(String::trim)
                    .anyMatch(day -> day.equalsIgnoreCase(targetDayOfWeek.name()));
            if (!dayMatches) {
                return false;
            }
        }

        // Validar rango de horarios
        if (price.getStartTime() != null && price.getEndTime() != null) {
            if (price.getStartTime().isBefore(price.getEndTime()) || price.getStartTime().equals(price.getEndTime())) {
                return !targetTime.isBefore(price.getStartTime()) && !targetTime.isAfter(price.getEndTime());
            } else {
                // Horario nocturno que cruza la medianoche (ej: 20:00 a 02:00)
                return !targetTime.isBefore(price.getStartTime()) || !targetTime.isAfter(price.getEndTime());
            }
        } else if (price.getStartTime() != null && targetTime.isBefore(price.getStartTime())) {
            return false;
        } else return price.getEndTime() == null || !targetTime.isAfter(price.getEndTime());
    }
}
