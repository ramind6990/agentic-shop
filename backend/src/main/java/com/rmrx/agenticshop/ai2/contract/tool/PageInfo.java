package com.rmrx.agenticshop.ai2.contract.tool;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/**
 * Where a page of a list result stands.
 *
 * @param total   all matches, not only this page
 * @param page    this page, 1-based
 * @param hasMore whether more matches exist after this page
 * @param note    set on the last page a tool may list while more matches exist: narrow the search to see them
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageInfo(long total, int page, boolean hasMore, String note) implements Serializable {
}
