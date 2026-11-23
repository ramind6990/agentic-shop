package com.rmrx.agenticshop.common.dto;

import com.rmrx.agenticshop.catalog.entity.Brand;
import com.rmrx.agenticshop.catalog.entity.Category;
import com.rmrx.agenticshop.catalog.entity.Product;
import com.rmrx.agenticshop.identity.entity.AppUser;
import com.rmrx.agenticshop.seller.entity.Seller;
import org.mapstruct.Mapper;

/** Shared MapStruct conversions from associated entities to compact {@link Ref}s. */
@Mapper
public interface RefMapper {

    default Ref brand(Brand b) {
        return b == null ? null : new Ref(b.getId(), b.getName());
    }

    default Ref seller(Seller s) {
        return s == null ? null : new Ref(s.getId(), s.getName());
    }

    default Ref category(Category c) {
        return c == null ? null : new Ref(c.getId(), c.getName());
    }

    default Ref user(AppUser u) {
        return u == null ? null : new Ref(u.getId(), u.getFullName());
    }

    default Ref product(Product p) {
        return p == null ? null : new Ref(p.getId(), p.getTitle());
    }
}
