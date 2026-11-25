package com.rmrx.agenticshop.app.catalog.service;

import com.rmrx.agenticshop.app.catalog.dto.ProductDtos.*;
import com.rmrx.agenticshop.app.catalog.entity.Inventory;
import com.rmrx.agenticshop.app.catalog.entity.Product;
import com.rmrx.agenticshop.app.catalog.entity.ProductImage;
import com.rmrx.agenticshop.app.catalog.entity.Review;
import com.rmrx.agenticshop.app.common.dto.RefMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(uses = RefMapper.class)
public interface CatalogMapper {

    @Mapping(target = "thumbnailUrl", expression = "java(thumbnail(product))")
    @Mapping(target = "inStock", source = "inStock")
    ProductSummary toSummary(Product product, boolean inStock);

    ProductImageDto toImage(ProductImage image);

    @Mapping(target = "authorName", expression = "java(com.rmrx.agenticshop.app.common.dto.DisplayNames.publicName(review.getCustomer().getFullName()))")
    @Mapping(target = "verifiedPurchase", expression = "java(review.getOrderItem() != null)")
    ReviewDto toReview(Review review);

    CustomerReviewDto toCustomerReview(Review review);

    default StockDto toStock(Inventory inv) {
        if (inv == null) return new StockDto(0, false, null, null);
        int available = Math.max(inv.getQtyAvailable() - inv.getQtyReserved(), 0);
        return new StockDto(available, available > 0, inv.getWarehouseCode(), inv.getRestockEta());
    }

    default String thumbnail(Product product) {
        return product.getImages().isEmpty() ? null : product.getImages().getFirst().getUrl();
    }
}
