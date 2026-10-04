package com.lms.catalogue.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * Create/edit form for {@link com.lms.common.domain.Book}. Field names
 * match what {@code components/form-field.html} binds to in
 * {@code catalogue/book-form.html} via {@code th:field}.
 *
 * <p><b>{@code authorId} is a single, primary author</b> — UC-02's own
 * field list for adding a book is singular ("the title, author, category,
 * ISBN, number of copies, and other relevant details"), and {@code
 * form-field.html}'s {@code type='select'} is a single-choice
 * {@code <select>}, not a multi-select. The schema's {@code BookAuthor}
 * table can hold more than one author per title (it carries an {@code
 * AuthorOrder}) — this form only ever writes {@code AuthorOrder = 1}. A
 * second contributor can be added directly against the {@code BookAuthor}
 * table later; it is out of this form's scope, not out of the schema's.
 *
 * <p><b>{@code categoryIds} is genuinely multi-valued</b> — a book
 * commonly belongs to more than one category, and {@code BookCategory} has
 * no attribute of its own to make one category "primary" the way {@code
 * AuthorOrder} does for authors. {@code form-field.html} has no multi-
 * select type, so {@code catalogue/book-form.html} binds this list against
 * a hand-written group of checkboxes instead of the shared fragment — the
 * same kind of small, commented exception {@code reset-password.html}
 * already makes for its hidden token field.
 *
 * <p><b>{@code keywords}</b> is a single comma-separated text field, not
 * its own management screen — {@code BookKeyword} is a flat multivalued
 * attribute (R1) with no other data of its own, and this task's scope for
 * "create/edit/deactivate" flows is books, copies, authors, categories and
 * publishers, not a sixth screen for keywords.
 */
@Getter
@Setter
public class BookForm {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must be at most 255 characters")
    private String title;

    @Size(max = 255, message = "Subtitle must be at most 255 characters")
    private String subtitle;

    /** CK_Book_ISBN13Format: exactly 13 digits. */
    @NotBlank(message = "ISBN-13 is required")
    @Pattern(regexp = "^\\d{13}$", message = "ISBN-13 must be exactly 13 digits")
    private String isbn13;

    /** CK_Book_ISBN10Format: 9 digits then a digit or X, optional field. */
    @Pattern(regexp = "^$|^\\d{9}[0-9Xx]$", message = "ISBN-10 must be 9 digits followed by a digit or X")
    private String isbn10;

    /** Book.PublisherID is nullable — "— None —" is a valid choice, see options builder in BookController. */
    private Integer publisherId;

    /** CK_Book_PublicationYear allows 1450..currentYear+1; the upper bound here is a generous static
     *  sanity check, not a mirror of "this year" — the database CHECK is the authoritative bound. */
    @Min(value = 1450, message = "Publication year must be 1450 or later")
    @Max(value = 2100, message = "Publication year is too far in the future")
    private Short publicationYear;

    @Size(max = 50, message = "Edition must be at most 50 characters")
    private String edition;

    /** CK_Book_LanguageCodeFormat: 2-3 lowercase letters. */
    @NotBlank(message = "Language code is required")
    @Pattern(regexp = "^[a-z]{2,3}$", message = "Language code must be 2-3 lowercase letters, e.g. 'en'")
    private String languageCode = "en";

    private String description;

    @NotNull(message = "Select a primary author")
    private Integer authorId;

    private List<Integer> categoryIds = new ArrayList<>();

    /** Comma-separated; parsed and lower-cased in BookService. */
    private String keywords;
}
