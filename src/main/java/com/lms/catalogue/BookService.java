package com.lms.catalogue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.catalogue.dto.BookForm;
import com.lms.catalogue.dto.BookListRow;
import com.lms.common.domain.Author;
import com.lms.common.domain.AuthorRepository;
import com.lms.common.domain.Book;
import com.lms.common.domain.BookAuthor;
import com.lms.common.domain.BookCopyRepository;
import com.lms.common.domain.BookCopyStatus;
import com.lms.common.domain.BookRepository;
import com.lms.common.domain.Category;
import com.lms.common.domain.CategoryRepository;
import com.lms.common.domain.LoanRepository;
import com.lms.common.domain.LoanStatus;
import com.lms.common.domain.Publisher;
import com.lms.common.domain.PublisherRepository;
import com.lms.common.web.SelectOption;

/**
 * UC-02 — the {@link Book} half of "Book Catalogue and Inventory". {@link
 * BookCopyService} owns {@link com.lms.common.domain.BookCopy}; the
 * {@code Author}/{@code Category}/{@code Publisher} reference data each
 * have their own small service.
 *
 * <p>Search/list ({@link #search}) and {@link #findById} are read-only and
 * called from a {@code permitAll} controller method — see {@code
 * SecurityConfig}'s catalogue matchers — because PB-22/PB-23 require a
 * guest to browse and search the catalogue without logging in. Every
 * method that writes ({@link #create}, {@link #update}, {@link
 * #deactivate}) is {@code @PreAuthorize}-gated to the two UC-02 actors
 * (business-rules.md / docs/scenarios.pdf: "Primary Actor: Librarian,
 * Supporting Actor: Library Administrator").
 */
@Service
@Transactional
public class BookService {

    /** Rows per page for the catalogue list/search screen. */
    static final int PAGE_SIZE = 20;

    private final BookRepository books;
    private final BookCopyRepository copies;
    private final AuthorRepository authors;
    private final CategoryRepository categories;
    private final PublisherRepository publishers;
    private final LoanRepository loans;

    public BookService(BookRepository books, BookCopyRepository copies, AuthorRepository authors,
            CategoryRepository categories, PublisherRepository publishers, LoanRepository loans) {
        this.books = books;
        this.copies = copies;
        this.authors = authors;
        this.categories = categories;
        this.publishers = publishers;
        this.loans = loans;
    }

    /**
     * The public catalogue list/search screen (UC-02 main flow: "The
     * Librarian may search for a book by title, author, or category" —
     * extended here to ISBN and keyword, and open to a guest per
     * PB-22/PB-23). {@code q} matches title, ISBN-13, ISBN-10, author name
     * and keyword all at once; {@code categoryId} and {@code
     * availableOnly} narrow further; every filter applies together.
     *
     * @param page 1-based, as the rest of this codebase's pagination uses (see
     *             {@code components/pagination.html}).
     */
    @Transactional(readOnly = true)
    public Page<BookListRow> search(String q, Integer categoryId, boolean availableOnly,
            int page, String sort, String dir) {
        String likeQuery = (q == null || q.isBlank()) ? null : "%" + q.trim().toLowerCase() + "%";
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), PAGE_SIZE, sortFor(sort, dir));
        Page<Book> result = books.search(likeQuery, categoryId, availableOnly, BookCopyStatus.Available, pageable);
        return result.map(this::toRow);
    }

    /** Only real {@link Book} columns are sortable — an available-copy count is computed, not a column. */
    private static Sort sortFor(String sort, String dir) {
        String property = switch (sort == null ? "" : sort) {
            case "isbn13" -> "isbn13";
            case "publicationYear" -> "publicationYear";
            default -> "title";
        };
        return Sort.by("desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC, property);
    }

    private BookListRow toRow(Book book) {
        long available = copies.countByBookBookIdAndStatus(book.getBookId(), BookCopyStatus.Available);
        long total = copies.countByBookBookId(book.getBookId());
        return new BookListRow(book.getBookId(), book.getTitle(), book.getIsbn13(),
                primaryAuthorName(book), categoryNames(book),
                book.getPublisher() == null ? null : book.getPublisher().getPublisherName(),
                book.getPublicationYear(), available, total);
    }

    private static String primaryAuthorName(Book book) {
        return book.getAuthors().isEmpty() ? "Unknown" : book.getAuthors().get(0).getAuthor().getAuthorName();
    }

    /** Also used directly by {@code BookController#detail} — a book's category names, comma-joined,
     *  computed here rather than in the template: SpringEL has no safe, well-tested way to join a
     *  {@code Set<Category>} into a String, only a {@code Set<String>} (see {@code CurrentUserAdvice}'s
     *  {@code currentRoleNames} for that simpler case, used successfully in account.html). */
    public static String categoryNames(Book book) {
        if (book.getCategories().isEmpty()) {
            return "Uncategorised";
        }
        return book.getCategories().stream()
                .map(Category::getCategoryName)
                .sorted()
                .collect(Collectors.joining(", "));
    }

    /** The public book detail page (UC-02 postcondition: "guest visitors can search and view the latest book availability"). */
    @Transactional(readOnly = true)
    public Book findById(Integer bookId) {
        Book book = books.findById(bookId).orElseThrow(() -> new NoSuchElementException("Book not found"));
        // The detail page (and this class's own categoryNames helper, called
        // from the controller after this method's transaction — and its
        // Hibernate session — has already closed) reads authors, categories
        // and publisher. Touching each lazy association once here, while the
        // session is still open, loads it into the entity for good; a
        // collection Hibernate has already fully loaded stays readable after
        // the entity is detached, unlike one that was never touched at all.
        // BookAuthor.author is its own @ManyToOne(LAZY) — loading the
        // BookAuthor join rows above does not also load the Author each one
        // points to; each stays its own lazy proxy until touched.
        book.getAuthors().forEach(ba -> ba.getAuthor().getAuthorName());
        book.getCategories().size();
        if (book.getPublisher() != null) {
            book.getPublisher().getPublisherName();
        }
        return book;
    }

    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    public Book create(BookForm form) {
        rejectDuplicateIsbn(form, null);
        Book book = new Book();
        applyForm(book, form);
        return books.save(book);
    }

    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    public Book update(Integer bookId, BookForm form) {
        Book book = books.findById(bookId).orElseThrow(() -> new NoSuchElementException("Book not found"));
        rejectDuplicateIsbn(form, bookId);
        applyForm(book, form);
        return books.save(book);
    }

    private void rejectDuplicateIsbn(BookForm form, Integer excludingBookId) {
        books.findByIsbn13(form.getIsbn13())
                .filter(existing -> excludingBookId == null || !existing.getBookId().equals(excludingBookId))
                .ifPresent(existing -> {
                    throw new DuplicateFieldException("isbn13",
                            "ISBN " + form.getIsbn13() + " is already used by \"" + existing.getTitle() + "\".");
                });
        String isbn10 = form.getIsbn10();
        if (isbn10 != null && !isbn10.isBlank() && books.existsByIsbn10(isbn10)
                // the filtered unique index only blocks a genuine duplicate, not "this same book, unchanged"
                && !(excludingBookId != null && isbn10.equals(books.findById(excludingBookId)
                        .map(Book::getIsbn10).orElse(null)))) {
            throw new DuplicateFieldException("isbn10", "ISBN-10 " + isbn10 + " is already in use.");
        }
    }

    /** Populates {@link BookForm} for the edit page (GET) — also staff-only, since it exists only to feed the edit form. */
    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    @Transactional(readOnly = true)
    public BookForm forEdit(Integer bookId) {
        Book book = findById(bookId);
        BookForm form = new BookForm();
        form.setTitle(book.getTitle());
        form.setSubtitle(book.getSubtitle());
        form.setIsbn13(book.getIsbn13());
        form.setIsbn10(book.getIsbn10());
        form.setPublisherId(book.getPublisher() == null ? null : book.getPublisher().getPublisherId());
        form.setPublicationYear(book.getPublicationYear());
        form.setEdition(book.getEdition());
        form.setLanguageCode(book.getLanguageCode());
        form.setDescription(book.getDescription());
        form.setAuthorId(book.getAuthors().isEmpty() ? null : book.getAuthors().get(0).getAuthor().getAuthorId());
        form.setCategoryIds(book.getCategories().stream().map(Category::getCategoryId).toList());
        form.setKeywords(String.join(", ", book.getKeywords()));
        return form;
    }

    private void applyForm(Book book, BookForm form) {
        book.setTitle(form.getTitle().trim());
        book.setSubtitle(blankToNull(form.getSubtitle()));
        book.setIsbn13(form.getIsbn13().trim());
        book.setIsbn10(blankToNull(form.getIsbn10()));
        book.setPublicationYear(form.getPublicationYear());
        book.setEdition(blankToNull(form.getEdition()));
        book.setLanguageCode(form.getLanguageCode().trim());
        book.setDescription(blankToNull(form.getDescription()));

        Publisher publisher = form.getPublisherId() == null ? null
                : publishers.findById(form.getPublisherId())
                        .orElseThrow(() -> new NoSuchElementException("Selected publisher no longer exists"));
        book.setPublisher(publisher);

        // Not an unconditional clear()-then-add(): book.authors cascades
        // ALL with orphanRemoval, so clearing it schedules the existing
        // BookAuthor row for deletion — if the author is unchanged, a new
        // BookAuthor(book, author, 1) has the exact same composite id
        // (BookID, AuthorID) as the row Hibernate just scheduled to
        // remove, and flushing both in one go throws
        // NonUniqueObjectException. Only touch the collection when the
        // author actually changed.
        Integer desiredAuthorId = form.getAuthorId();
        boolean alreadyPrimaryAuthor = book.getAuthors().stream()
                .anyMatch(ba -> ba.getAuthor().getAuthorId().equals(desiredAuthorId));
        if (!alreadyPrimaryAuthor) {
            Author author = authors.findById(desiredAuthorId)
                    .orElseThrow(() -> new NoSuchElementException("Selected author no longer exists"));
            book.getAuthors().clear();
            book.getAuthors().add(new BookAuthor(book, author, 1));
        }

        Set<Category> selected = form.getCategoryIds() == null ? Set.of()
                : Set.copyOf(categories.findAllById(form.getCategoryIds()));
        book.getCategories().clear();
        book.getCategories().addAll(selected);

        book.getKeywords().clear();
        book.getKeywords().addAll(parseKeywords(form.getKeywords()));
    }

    private static Set<String> parseKeywords(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(k -> !k.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /**
     * UC-02 alternative flow: "delete a record that has no active
     * borrowing transactions" — this project deliberately never deletes a
     * row that other tables reference (see {@code LoanRepository}'s own
     * javadoc on {@code existsByCopyBookBookIdAndStatus}: "a book may only
     * be deleted when none of its copies is on an Active loan"), so
     * "delete" here is {@code Book.IsActive = 0}: gone from the public
     * list and search ({@link #search} always filters {@code active =
     * true}), but every historical {@link com.lms.common.domain.Loan} /
     * {@link com.lms.common.domain.BookReview} row that references it
     * stays valid.
     */
    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    public void deactivate(Integer bookId) {
        Book book = books.findById(bookId).orElseThrow(() -> new NoSuchElementException("Book not found"));
        if (loans.existsByCopyBookBookIdAndStatus(bookId, LoanStatus.Active)) {
            throw new ActiveLoanException(
                    "\"" + book.getTitle() + "\" has a copy currently on loan and cannot be deactivated "
                            + "until it is returned.");
        }
        book.setActive(false);
        books.save(book);
    }

    // ---- reference-data options for the book form's dropdowns/checkboxes ----

    @Transactional(readOnly = true)
    public List<SelectOption> authorOptions() {
        return authors.findByActiveTrueOrderByAuthorName().stream()
                .map(a -> new SelectOption(a.getAuthorId().toString(), a.getAuthorName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Category> categoryOptions() {
        return categories.findByActiveTrueOrderByCategoryName();
    }

    @Transactional(readOnly = true)
    public List<SelectOption> publisherOptions() {
        List<SelectOption> options = new ArrayList<>();
        options.add(new SelectOption("", "— None —"));
        publishers.findByActiveTrueOrderByPublisherName()
                .forEach(p -> options.add(new SelectOption(p.getPublisherId().toString(), p.getPublisherName())));
        return options;
    }

    /** For the search screen's category filter dropdown — "any category" plus every active one. */
    @Transactional(readOnly = true)
    public List<SelectOption> categoryFilterOptions() {
        List<SelectOption> options = new ArrayList<>();
        options.add(new SelectOption("", "All categories"));
        categories.findByActiveTrueOrderByCategoryName()
                .forEach(c -> options.add(new SelectOption(c.getCategoryId().toString(), c.getCategoryName())));
        return options;
    }
}
