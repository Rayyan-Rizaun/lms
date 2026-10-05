package com.lms.catalogue.dto;

/**
 * One row of the public book list/search screen (UC-02, PB-22/PB-23).
 * {@code com.lms.catalogue.BookService} builds this from a {@link
 * com.lms.common.domain.Book} plus a copy-availability count computed
 * alongside it — a typed row for {@code catalogue/books.html} rather than
 * handing the template a raw entity and a separate lookup map to join
 * itself (same reasoning as {@code com.lms.common.web.StatTile} / {@code
 * DataTableColumn}: a controller/service builds typed Java, not a
 * string-keyed structure a template has to interpret).
 *
 * @param authorName    the primary (AuthorOrder = 1) author's name, or
 *                       "Unknown" if the book has none yet.
 * @param categoryNames comma-joined category names, or "Uncategorised".
 * @param availableCopies copies currently {@code BookCopyStatus.Available}.
 * @param totalCopies     every copy of this title, any status.
 */
public record BookListRow(Integer bookId, String title, String isbn13, String authorName,
                           String categoryNames, String publisherName, Short publicationYear,
                           long availableCopies, long totalCopies) {
}
