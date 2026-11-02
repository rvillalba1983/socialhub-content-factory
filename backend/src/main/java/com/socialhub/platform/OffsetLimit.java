package com.socialhub.platform;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Paginación por {@code offset} y {@code limit} (guías de Zalando) sobre Spring Data,
 * que de serie solo pagina por número de página.
 */
public record OffsetLimit(long offset, int limit, Sort sort) implements Pageable {

    public OffsetLimit {
        if (offset < 0) throw new IllegalArgumentException("offset no puede ser negativo");
        if (limit < 1) throw new IllegalArgumentException("limit debe ser al menos 1");
        sort = sort == null ? Sort.unsorted() : sort;
    }

    public static OffsetLimit of(Integer offset, Integer limit, Sort sort) {
        return new OffsetLimit(offset == null ? 0 : offset, limit == null ? 100 : limit, sort);
    }

    @Override public int getPageNumber() { return (int) (offset / limit); }
    @Override public int getPageSize() { return limit; }
    @Override public long getOffset() { return offset; }
    @Override public Sort getSort() { return sort; }
    @Override public Pageable next() { return new OffsetLimit(offset + limit, limit, sort); }
    @Override public Pageable previousOrFirst() { return hasPrevious() ? new OffsetLimit(Math.max(0, offset - limit), limit, sort) : first(); }
    @Override public Pageable first() { return new OffsetLimit(0, limit, sort); }
    @Override public Pageable withPage(int pageNumber) { return new OffsetLimit((long) pageNumber * limit, limit, sort); }
    @Override public boolean hasPrevious() { return offset > 0; }
}
