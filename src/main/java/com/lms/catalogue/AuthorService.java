package com.lms.catalogue;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.catalogue.dto.AuthorForm;
import com.lms.common.domain.Author;
import com.lms.common.domain.AuthorRepository;

/**
 * UC-02 reference data: {@link Author}. A guest never reaches this
 * service directly (there is no public author page) — it exists so a
 * staff member can maintain the list {@link BookService#authorOptions()}
 * draws the Book form's author dropdown from. Every method is staff-only.
 */
@Service
@Transactional
public class AuthorService {

    private final AuthorRepository authors;

    public AuthorService(AuthorRepository authors) {
        this.authors = authors;
    }

    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    @Transactional(readOnly = true)
    public List<Author> findAll() {
        return authors.findAll(Sort.by("authorName"));
    }

    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    @Transactional(readOnly = true)
    public Author findById(Integer id) {
        return authors.findById(id).orElseThrow(() -> new NoSuchElementException("Author not found"));
    }

    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    public Author create(AuthorForm form) {
        rejectDuplicateName(form, null);
        Author author = new Author();
        applyForm(author, form);
        return authors.save(author);
    }

    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    public Author update(Integer id, AuthorForm form) {
        Author author = findById(id);
        rejectDuplicateName(form, id);
        applyForm(author, form);
        return authors.save(author);
    }

    private void rejectDuplicateName(AuthorForm form, Integer excludingId) {
        authors.findByAuthorNameIgnoreCase(form.getAuthorName().trim())
                .filter(existing -> excludingId == null || !existing.getAuthorId().equals(excludingId))
                .ifPresent(existing -> {
                    throw new DuplicateFieldException("authorName",
                            "\"" + form.getAuthorName() + "\" is already on file.");
                });
    }

    private void applyForm(Author author, AuthorForm form) {
        author.setAuthorName(form.getAuthorName().trim());
        author.setBiography(form.getBiography() == null || form.getBiography().isBlank()
                ? null : form.getBiography().trim());
    }

    /**
     * UC-02's deactivate flow, applied to reference data rather than a
     * book or copy: {@code Author.IsActive = 0} removes it from {@link
     * BookService#authorOptions()} (new/edited books can no longer be
     * credited to it) without touching any {@code BookAuthor} row a
     * published title already has — unlike a book or a copy, there is no
     * "in use" guard here, because deactivating an author never breaks an
     * existing reference the way deleting one would.
     */
    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    public void deactivate(Integer id) {
        Author author = findById(id);
        author.setActive(false);
        authors.save(author);
    }
}
