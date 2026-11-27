package com.rmrx.agenticshop.ai2.tools;

import com.rmrx.agenticshop.ai2.contract.tool.PageInfo;
import com.rmrx.agenticshop.app.common.dto.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Paging of every list tool: pages of {@value #PAGE_SIZE}, 1-based, at most {@value #MAX_PAGE}. A page beyond that is
 * refused with a message telling the model to narrow the search: paging is for "show me more", not for searching.
 */
public final class ToolPaging {

    public static final int PAGE_SIZE = 10;
    public static final int MAX_PAGE = 5;

    static final String TOO_DEEP = "Only the first " + MAX_PAGE + " pages (" + MAX_PAGE * PAGE_SIZE
            + " results) can be listed. Narrow the search with filters or another sort instead.";
    static final String LAST_PAGE = "This is the last page that can be listed; narrow the search to see the others.";

    private ToolPaging() {
    }

    /** The page to load; {@code page} null means the first one. */
    public static Pageable pageable(Integer page, Sort sort) {
        int p = page == null ? 1 : page;
        if (p < 1) throw new IllegalArgumentException("page starts at 1.");
        if (p > MAX_PAGE) throw new IllegalArgumentException(TOO_DEEP);
        return PageRequest.of(p - 1, PAGE_SIZE, sort);
    }

    public static PageInfo info(PageResponse<?> page) {
        int number = page.page() + 1;
        boolean hasMore = number < page.totalPages();
        return new PageInfo(page.totalElements(), number, hasMore, hasMore && number == MAX_PAGE ? LAST_PAGE : null);
    }
}
