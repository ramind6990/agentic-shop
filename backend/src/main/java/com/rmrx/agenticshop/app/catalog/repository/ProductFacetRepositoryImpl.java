package com.rmrx.agenticshop.app.catalog.repository;

import com.rmrx.agenticshop.app.catalog.entity.Product;
import com.rmrx.agenticshop.app.catalog.entity.ProductAttributeValue;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

class ProductFacetRepositoryImpl implements ProductFacetRepository {

    private final EntityManager em;

    ProductFacetRepositoryImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<Tuple> countByBrand(Specification<Product> spec) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<Product> p = cq.from(Product.class);
        Join<Object, Object> brand = p.join("brand");
        Expression<Long> count = cb.count(p);
        cq.multiselect(brand.get("id"), brand.get("name"), count)
                .where(spec.toPredicate(p, cq, cb))
                .groupBy(brand.get("id"), brand.get("name"))
                .orderBy(cb.desc(count), cb.asc(brand.get("name")));
        return em.createQuery(cq).getResultList();
    }

    @Override
    public List<Tuple> countByCategory(Specification<Product> spec) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<Product> p = cq.from(Product.class);
        Path<Long> categoryId = p.get("category").get("id");
        cq.multiselect(categoryId, cb.count(p))
                .where(spec.toPredicate(p, cq, cb))
                .groupBy(categoryId);
        return em.createQuery(cq).getResultList();
    }

    @Override
    public Tuple priceStats(Specification<Product> spec) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<Product> p = cq.from(Product.class);
        Path<BigDecimal> price = p.get("price");
        cq.multiselect(cb.min(price), cb.max(price), cb.count(p))
                .where(spec.toPredicate(p, cq, cb));
        return em.createQuery(cq).getSingleResult();
    }

    @Override
    public Tuple ratingStats(Specification<Product> spec) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<Product> p = cq.from(Product.class);
        Path<BigDecimal> rating = p.get("ratingAvg");
        cq.multiselect(
                        atLeast(cb, rating, 4),
                        atLeast(cb, rating, 3),
                        atLeast(cb, rating, 2),
                        atLeast(cb, rating, 1))
                .where(spec.toPredicate(p, cq, cb));
        return em.createQuery(cq).getSingleResult();
    }

    private static Expression<Long> atLeast(CriteriaBuilder cb, Path<BigDecimal> rating, int stars) {
        return cb.sum(cb.<Long>selectCase()
                .when(cb.greaterThanOrEqualTo(rating, BigDecimal.valueOf(stars)), 1L)
                .otherwise(0L));
    }

    @Override
    public List<Tuple> countAttributeValues(Specification<Product> spec, Collection<Long> attributeIds) {
        if (attributeIds.isEmpty()) return List.of();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<ProductAttributeValue> v = cq.from(ProductAttributeValue.class);

        Subquery<Long> matching = cq.subquery(Long.class);
        Root<Product> p = matching.from(Product.class);
        matching.select(p.get("id")).where(spec.toPredicate(p, cq, cb));

        Path<Long> attributeId = v.get("attribute").get("id");
        Path<Long> optionId = v.get("option").get("id");
        Path<BigDecimal> numberValue = v.get("numberValue");
        Path<Boolean> boolValue = v.get("boolValue");
        cq.multiselect(attributeId, optionId, numberValue, boolValue, cb.count(v))
                .where(attributeId.in(attributeIds), v.get("product").get("id").in(matching))
                .groupBy(attributeId, optionId, numberValue, boolValue);
        return em.createQuery(cq).getResultList();
    }
}
