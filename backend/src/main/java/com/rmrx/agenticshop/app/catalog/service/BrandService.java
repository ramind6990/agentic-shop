package com.rmrx.agenticshop.app.catalog.service;

import com.rmrx.agenticshop.app.catalog.repository.BrandRepository;
import com.rmrx.agenticshop.app.common.dto.PageResponse;
import com.rmrx.agenticshop.app.common.dto.Ref;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BrandService {

    private final BrandRepository brandRepository;
    private final CatalogMetadata metadata;

    public BrandService(BrandRepository brandRepository, CatalogMetadata metadata) {
        this.brandRepository = brandRepository;
        this.metadata = metadata;
    }

    public PageResponse<Ref> search(String q, Long categoryId, Pageable pageable) {
        String text = q == null ? "" : q.trim();
        var page = categoryId == null
                ? brandRepository.findByNameContainingIgnoreCase(text, pageable)
                : brandRepository.findInCategory(text, metadata.category(categoryId).path(), pageable);
        return PageResponse.of(page, b -> new Ref(b.getId(), b.getName()));
    }
}
