package lat.nexofood.api.modules.catalog.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.catalog.domain.Category;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.domain.Tax;
import lat.nexofood.api.modules.catalog.infrastructure.repository.CategoryRepository;
import lat.nexofood.api.modules.catalog.infrastructure.repository.ProductRepository;
import lat.nexofood.api.modules.catalog.infrastructure.repository.TaxRepository;
import lat.nexofood.api.modules.catalog.web.dto.request.ProductRequest;
import lat.nexofood.api.modules.catalog.web.dto.response.ProductResponse;
import lat.nexofood.api.modules.catalog.web.mapper.ProductMapper;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final TenantRepository tenantRepository;
    private final CategoryRepository categoryRepository;
    private final TaxRepository taxRepository;
    private final ProductMapper productMapper;

    @Override
    @Transactional
    public ProductResponse create(UUID tenantId, ProductRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado con ID: " + tenantId));

        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findByIdAndTenantId(request.categoryId(), tenantId)
                    .orElseThrow(() -> new BadRequestException("La categoría especificada no pertenece a la tienda actual."));
        }

        Tax tax = null;
        if (request.taxId() != null) {
            tax = validateAndGetTaxForTenant(request.taxId(), tenantId);
        }

        Product product = productMapper.toEntity(request, tenant, category, tax);
        Product savedProduct = productRepository.save(product);
        return productMapper.toResponse(savedProduct);
    }

    @Override
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
