package lat.nexofood.api.modules.catalog.web.mapper;

import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.domain.ProductPrice;
import lat.nexofood.api.modules.catalog.web.dto.request.ProductPriceRequest;
import lat.nexofood.api.modules.catalog.web.dto.response.ProductPriceResponse;
import org.springframework.stereotype.Component;

@Component
public class ProductPriceMapper {

    public ProductPriceResponse toResponse(ProductPrice price) {
        if (price == null) {
            return null;
        }
        return ProductPriceResponse.builder()
                .id(price.getId())
                .productId(price.getProduct() != null ? price.getProduct().getId() : null)
                .name(price.getName())
                .price(price.getPrice())
                .isBase(price.getIsBase())
                .discountPercentage(price.getDiscountPercentage())
                .startDate(price.getStartDate())
                .endDate(price.getEndDate())
                .startTime(price.getStartTime())
                .endTime(price.getEndTime())
                .daysOfWeek(price.getDaysOfWeek())
                .priority(price.getPriority())
                .isActive(price.getIsActive())
                .createdAt(price.getCreatedAt())
                .updatedAt(price.getUpdatedAt())
                .build();
    }

    public ProductPrice toEntity(ProductPriceRequest request, Product product) {
        if (request == null) {
            return null;
        }
        return ProductPrice.builder()
                .id(request.id())
                .product(product)
                .name(request.name())
                .price(request.price())
                .isBase(request.isBase() != null ? request.isBase() : false)
                .discountPercentage(request.discountPercentage())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .daysOfWeek(request.daysOfWeek())
                .priority(request.priority() != null ? request.priority() : 0)
                .isActive(request.isActive() != null ? request.isActive() : true)
                .build();
    }
}
