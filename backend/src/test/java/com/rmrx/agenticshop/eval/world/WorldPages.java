package com.rmrx.agenticshop.eval.world;

import com.rmrx.agenticshop.app.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

/** One page of a world list, in the shop's {@link PageResponse} shape. */
final class WorldPages {

    private WorldPages() {
    }

    static <T> PageResponse<T> page(List<T> all, Pageable pageable) {
        int size = pageable.isPaged() ? pageable.getPageSize() : Math.max(1, all.size());
        int number = pageable.isPaged() ? pageable.getPageNumber() : 0;
        int from = Math.min(all.size(), number * size);
        int to = Math.min(all.size(), from + size);
        int totalPages = all.isEmpty() ? 0 : (all.size() + size - 1) / size;
        return new PageResponse<>(all.subList(from, to), number, size, all.size(), totalPages);
    }
}
