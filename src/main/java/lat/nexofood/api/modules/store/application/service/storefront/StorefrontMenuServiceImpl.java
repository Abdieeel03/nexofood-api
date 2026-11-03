package lat.nexofood.api.modules.store.application.service.storefront;

import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.catalog.application.service.ProductPricingService;
import lat.nexofood.api.modules.catalog.application.service.TaxCalculationResult;
import lat.nexofood.api.modules.catalog.application.service.TaxCalculationService;
import lat.nexofood.api.modules.catalog.domain.Category;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.domain.ProductPrice;
import lat.nexofood.api.modules.catalog.domain.Tax;
import lat.nexofood.api.modules.catalog.infrastructure.repository.CategoryRepository;
import lat.nexofood.api.modules.catalog.infrastructure.repository.ProductRepository;
import lat.nexofood.api.modules.catalog.web.dto.response.CategoryResponse;
import lat.nexofood.api.modules.catalog.web.mapper.CategoryMapper;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.store.web.dto.response.StorefrontCategoryResponse;
import lat.nexofood.api.modules.store.web.dto.response.StorefrontMenuResponse;
import lat.nexofood.api.modules.store.web.dto.response.StorefrontProductResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StorefrontMenuServiceImpl implements StorefrontMenuService {

    private final TenantRepository tenantRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductPricingService productPricingService;
    private final TaxCalculationService taxCalculationService;
    private final CategoryMapper categoryMapper;

    @Autowired
    public StorefrontMenuServiceImpl(
            TenantRepository tenantRepository,
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            ProductPricingService productPricingService,
            TaxCalculationService taxCalculationService,
            CategoryMapper categoryMapper
    ) {
        this.tenantRepository = tenantRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.productPricingService = productPricingService;
        this.taxCalculationService = taxCalculationService;
        this.categoryMapper = categoryMapper;
    }

    public StorefrontMenuServiceImpl(
            TenantRepository tenantRepository,
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            ProductPricingService productPricingService,
            TaxCalculationService taxCalculationService
    ) {
        this(
                tenantRepository,
                categoryRepository,
                productRepository,
                productPricingService,
                taxCalculationService,
                new CategoryMapper()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public StorefrontMenuResponse getMenuBySlug(String slug) {
        Tenant tenant = getActiveTenantBySlugOrThrow(slug);
        LocalDateTime now = LocalDateTime.now();

        List<Category> activeCategories = categoryRepository
                .findAllByTenantIdAndIsActiveTrueOrderBySortOrderAsc(tenant.getId());

        List<Product> availableProducts = productRepository
                .findAllByTenantIdAndIsAvailableTrue(tenant.getId());

        Map<UUID, List<Product>> productsByCategoryId = availableProducts.stream()
                .filter(p -> p.getCategory() != null && p.getCategory().getId() != null)
                .collect(Collectors.groupingBy(p -> p.getCategory().getId()));

        List<StorefrontCategoryResponse> categoryResponses = new ArrayList<>();
        for (Category category : activeCategories) {
            List<Product> catProducts = productsByCategoryId.getOrDefault(category.getId(), List.of());
            List<StorefrontProductResponse> mappedProducts = catProducts.stream()
                    .map(p -> toStorefrontProduct(p, now))
                    .toList();

            categoryResponses.add(
                    StorefrontCategoryResponse.builder()
                            .id(category.getId())
                            .name(category.getName())
                            .description(category.getDescription())
                            .sortOrder(category.getSortOrder())
                            .products(mappedProducts)
                            .build()
            );
        }

        List<StorefrontProductResponse> uncategorized = availableProducts.stream()
                .filter(p -> p.getCategory() == null)
                .map(p -> toStorefrontProduct(p, now))
                .toList();

        return StorefrontMenuResponse.builder()
                .tenantId(tenant.getId())
                .tenantName(tenant.getName())
                .tenantSlug(tenant.getSlug())
                .logoUrl(tenant.getLogoUrl())
                .bannerUrl(tenant.getBannerUrl())
                .categories(categoryResponses)
                .uncategorizedProducts(uncategorized)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getActiveCategoriesBySlug(String slug) {
        Tenant tenant = getActiveTenantBySlugOrThrow(slug);
        return categoryRepository.findAllByTenantIdAndIsActiveTrueOrderBySortOrderAsc(tenant.getId())
                .stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StorefrontProductResponse> getProductsBySlug(
            String slug,
            UUID categoryId,
            String search,
            Pageable pageable
    ) {
        Tenant tenant = getActiveTenantBySlugOrThrow(slug);
        LocalDateTime now = LocalDateTime.now();
        boolean hasSearch = search != null && !search.isBlank();
        String normalizedSearch = hasSearch ? search.trim() : null;

        Page<Product> productPage;
        if (categoryId != null) {
            Category category = categoryRepository.findByIdAndTenantId(categoryId, tenant.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada en esta tienda"));
            if (!Boolean.TRUE.equals(category.getIsActive())) {
                throw new ResourceNotFoundException("La categoría solicitada no se encuentra disponible");
            }
            if (hasSearch) {
                productPage = productRepository.findAllByTenantIdAndCategoryIdAndIsAvailableTrueAndNameContainingIgnoreCase(
                        tenant.getId(), categoryId, normalizedSearch, pageable
                );
            } else {
                productPage = productRepository.findAllByTenantIdAndCategoryIdAndIsAvailableTrue(
                        tenant.getId(), categoryId, pageable
                );
            }
        } else {
            if (hasSearch) {
                productPage = productRepository.findStorefrontProductsByTenantAndSearch(
                        tenant.getId(), normalizedSearch, pageable
                );
            } else {
                productPage = productRepository.findStorefrontProductsByTenant(
                        tenant.getId(), pageable
                );
            }
        }

        Page<StorefrontProductResponse> mappedPage = productPage.map(p -> toStorefrontProduct(p, now));
        return PageResponse.from(mappedPage);
    }

    @Override
    @Transactional(readOnly = true)
    public StorefrontProductResponse getProductDetailBySlug(String slug, UUID productId) {
        Tenant tenant = getActiveTenantBySlugOrThrow(slug);

        Product product = productRepository.findByIdAndTenantId(productId, tenant.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado en esta tienda"));

        if (!Boolean.TRUE.equals(product.getIsAvailable())) {
            throw new ResourceNotFoundException("El producto solicitado no se encuentra disponible actualmente");
        }

        if (product.getCategory() != null && !Boolean.TRUE.equals(product.getCategory().getIsActive())) {
            throw new ResourceNotFoundException("El producto solicitado no se encuentra disponible actualmente");
        }

        return toStorefrontProduct(product, LocalDateTime.now());
    }

    private Tenant getActiveTenantBySlugOrThrow(String slug) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado con slug: " + slug));

        if (!Boolean.TRUE.equals(tenant.getIsActive())) {
            throw new ResourceNotFoundException("El restaurante '" + tenant.getName() + "' no se encuentra activo");
        }
        return tenant;
    }

    private StorefrontProductResponse toStorefrontProduct(Product product, LocalDateTime evaluationTime) {
        BigDecimal currentPrice = productPricingService.calculateCurrentPrice(product, evaluationTime);
        BigDecimal basePrice = productPricingService.getBasePrice(product);
        ProductPrice effectiveRule = productPricingService.getEffectivePriceRule(product, evaluationTime);

        String activePromotionName = null;
        BigDecimal discountPercentage = null;
        if (effectiveRule != null && !Boolean.TRUE.equals(effectiveRule.getIsBase())) {
            activePromotionName = effectiveRule.getName();
            discountPercentage = effectiveRule.getDiscountPercentage();
        }

        Tax tax = product.getTax();
        TaxCalculationResult taxCalc = taxCalculationService.calculate(currentPrice, tax, 1);
        BigDecimal finalPrice = taxCalc.subtotal().add(taxCalc.taxAmount());
        Tax activeTax = taxCalc.tax();

        return StorefrontProductResponse.builder()
                .id(product.getId())
                .tenantId(product.getTenant() != null ? product.getTenant().getId() : null)
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .name(product.getName())
                .description(product.getDescription())
                .imageUrl(product.getImageUrl())
                .price(currentPrice)
                .basePrice(basePrice)
                .activePromotionName(activePromotionName)
                .discountPercentage(discountPercentage)
                .taxId(activeTax != null ? activeTax.getId() : null)
                .taxName(activeTax != null ? activeTax.getName() : null)
                .taxRate(taxCalc.taxRate())
                .taxInclusive(activeTax != null ? activeTax.getIsInclusive() : null)
                .baseUnitPrice(taxCalc.baseUnitPrice())
                .taxAmount(taxCalc.taxAmount())
                .finalPrice(finalPrice)
                .isAvailable(product.getIsAvailable())
                .build();
    }
}
