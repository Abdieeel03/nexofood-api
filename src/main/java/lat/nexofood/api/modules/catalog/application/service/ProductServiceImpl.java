package lat.nexofood.api.modules.catalog.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.catalog.domain.Category;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.domain.ProductPrice;
import lat.nexofood.api.modules.catalog.domain.Tax;
import lat.nexofood.api.modules.catalog.infrastructure.repository.CategoryRepository;
import lat.nexofood.api.modules.catalog.infrastructure.repository.ProductRepository;
import lat.nexofood.api.modules.catalog.infrastructure.repository.TaxRepository;
import lat.nexofood.api.modules.catalog.web.dto.request.ProductPriceRequest;
import lat.nexofood.api.modules.catalog.web.dto.request.ProductRequest;
import lat.nexofood.api.modules.catalog.web.dto.response.ProductResponse;
import lat.nexofood.api.modules.catalog.web.mapper.ProductMapper;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.subscription.application.service.guard.SubscriptionGuard;
import lat.nexofood.api.modules.subscription.domain.SubscriptionPlan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final TenantRepository tenantRepository;
    private final CategoryRepository categoryRepository;
    private final TaxRepository taxRepository;
    private final ProductMapper productMapper;
    private final SubscriptionGuard subscriptionGuard;

    @Autowired
    public ProductServiceImpl(
            ProductRepository productRepository,
            TenantRepository tenantRepository,
            CategoryRepository categoryRepository,
            TaxRepository taxRepository,
            ProductMapper productMapper,
            SubscriptionGuard subscriptionGuard
    ) {
        this.productRepository = productRepository;
        this.tenantRepository = tenantRepository;
        this.categoryRepository = categoryRepository;
        this.taxRepository = taxRepository;
        this.productMapper = productMapper;
        this.subscriptionGuard = subscriptionGuard;
    }

    public ProductServiceImpl(
            ProductRepository productRepository,
            TenantRepository tenantRepository,
            CategoryRepository categoryRepository,
            TaxRepository taxRepository,
            ProductMapper productMapper
    ) {
        this(productRepository, tenantRepository, categoryRepository, taxRepository, productMapper, null);
    }

    @Override
    @CacheEvict(value = "storefront_menu", allEntries = true)
    @Transactional
    public ProductResponse create(UUID tenantId, ProductRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado con ID: " + tenantId));

        validateSubscriptionProductLimit(tenantId);

        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findByIdAndTenantId(request.categoryId(), tenantId)
                    .orElseThrow(() -> new BadRequestException("La categoría especificada no pertenece a la tienda actual."));
        }

        Tax tax = null;
        if (request.taxId() != null) {
            tax = validateAndGetTaxForTenant(request.taxId(), tenantId);
        }

        validateInitialPrices(request);

        Product product = productMapper.toEntity(request, tenant, category, tax);
        Product savedProduct = productRepository.save(product);
        return productMapper.toResponse(savedProduct);
    }

    @Override
    @CacheEvict(value = "storefront_menu", allEntries = true)
    @Transactional
    public ProductResponse update(UUID tenantId, UUID productId, ProductRequest request) {
        Product product = productRepository.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + productId));

        if (request.categoryId() != null) {
            Category category = categoryRepository.findByIdAndTenantId(request.categoryId(), tenantId)
                    .orElseThrow(() -> new BadRequestException("La categoría especificada no pertenece a la tienda actual."));
            product.setCategory(category);
        } else {
            product.setCategory(null);
        }

        if (request.taxId() != null) {
            Tax tax = validateAndGetTaxForTenant(request.taxId(), tenantId);
            product.setTax(tax);
        } else {
            product.setTax(null);
        }

        product.setName(request.name());
        product.setDescription(request.description());
        product.setImageUrl(request.imageUrl());
        if (request.isAvailable() != null) {
            product.setIsAvailable(request.isAvailable());
        }

        if (request.price() != null) {
            updateOrAttachBasePrice(product, request);
        }

        Product updatedProduct = productRepository.save(product);
        return productMapper.toResponse(updatedProduct);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse findByIdAndTenant(UUID tenantId, UUID productId) {
        Product product = productRepository.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + productId));
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> findAllByTenant(UUID tenantId) {
        return productRepository.findAllByTenantId(tenantId)
                .stream()
                .map(productMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> findAllByTenant(
            UUID tenantId,
            UUID categoryId,
            Boolean availableOnly,
            Pageable pageable
    ) {
        Page<Product> productPage;
        boolean filterAvailable = Boolean.TRUE.equals(availableOnly);

        if (categoryId != null && filterAvailable) {
            productPage = productRepository.findAllByTenantIdAndCategoryIdAndIsAvailableTrue(tenantId, categoryId, pageable);
        } else if (categoryId != null) {
            productPage = productRepository.findAllByTenantIdAndCategoryId(tenantId, categoryId, pageable);
        } else if (filterAvailable) {
            productPage = productRepository.findAllByTenantIdAndIsAvailableTrue(tenantId, pageable);
        } else {
            productPage = productRepository.findAllByTenantId(tenantId, pageable);
        }

        Page<ProductResponse> mappedPage = productPage.map(productMapper::toResponse);
        return PageResponse.from(mappedPage);
    }

    @Override
    @CacheEvict(value = "storefront_menu", allEntries = true)
    @Transactional
    public ProductResponse toggleAvailability(UUID tenantId, UUID productId, boolean isAvailable) {
        Product product = productRepository.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + productId));

        product.setIsAvailable(isAvailable);
        Product updated = productRepository.save(product);
        return productMapper.toResponse(updated);
    }

    @Override
    @CacheEvict(value = "storefront_menu", allEntries = true)
    @Transactional
    public void delete(UUID tenantId, UUID productId) {
        Product product = productRepository.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + productId));

        product.setIsAvailable(false);
        productRepository.save(product);
    }

    private void validateSubscriptionProductLimit(UUID tenantId) {
        if (subscriptionGuard == null) {
            return;
        }
        SubscriptionPlan plan = subscriptionGuard.getLimits(tenantId);
        if (plan != null && plan.getMaxProducts() != null) {
            long currentCount = productRepository.countByTenantId(tenantId);
            if (currentCount >= plan.getMaxProducts()) {
                throw new BadRequestException(
                        "Se ha alcanzado el límite máximo de productos permitidos por su plan de suscripción ("
                                + plan.getMaxProducts() + ")."
                );
            }
        }
    }

    private void validateInitialPrices(ProductRequest request) {
        boolean hasDirectBasePrice = request.price() != null;
        long activeBaseInList = 0;

        if (request.prices() != null) {
            for (ProductPriceRequest priceReq : request.prices()) {
                boolean isBase = Boolean.TRUE.equals(priceReq.isBase());
                boolean isActive = priceReq.isActive() == null || Boolean.TRUE.equals(priceReq.isActive());
                if (isBase && isActive) {
                    activeBaseInList++;
                }
            }
        }

        long totalActiveBase = (hasDirectBasePrice ? 1 : 0) + activeBaseInList;
        if (totalActiveBase == 0) {
            throw new BadRequestException("El producto debe tener un precio base definido.");
        }
        if (totalActiveBase > 1) {
            throw new ResourceConflictException("El producto solo puede tener un único precio base activo.");
        }
    }

    private void updateOrAttachBasePrice(Product product, ProductRequest request) {
        if (product.getPrices() == null) {
            product.setPrices(new ArrayList<>());
        }

        ProductPrice basePrice = product.getPrices().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsBase()))
                .findFirst()
                .orElse(null);

        if (basePrice != null) {
            basePrice.setPrice(request.price());
            basePrice.setIsActive(true);
        } else {
            ProductPrice newBase = ProductPrice.builder()
                    .product(product)
                    .name("Precio Base")
                    .price(request.price())
                    .isBase(true)
                    .priority(0)
                    .isActive(true)
                    .build();
            product.getPrices().add(newBase);
        }
    }

    private Tax validateAndGetTaxForTenant(UUID taxId, UUID tenantId) {
        Tax tax = taxRepository.findByIdAndTenantId(taxId, tenantId)
                .orElseThrow(() -> new BadRequestException(
                        "El impuesto especificado (" + taxId + ") no existe o no pertenece al tenant actual (" + tenantId + ")."
                ));

        if (!Boolean.TRUE.equals(tax.getIsActive())) {
            throw new BadRequestException("No se puede asignar un impuesto inactivo a un producto.");
        }

        return tax;
    }
}
