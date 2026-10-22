package edu.university.grps.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** List envelope from PRD section 23: content, page, size, totalElements, totalPages. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    public static <T> PageResponse<T> of(List<T> all, int page, int size) {
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        int totalPages = size == 0 ? 0 : (int) Math.ceil(all.size() / (double) size);
        return new PageResponse<>(all.subList(from, to), page, size, all.size(), totalPages);
    }
}
