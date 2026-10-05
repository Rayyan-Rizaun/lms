package com.lms.catalogue;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.catalogue.dto.CategoryForm;
import com.lms.common.domain.Category;
import com.lms.common.domain.CategoryRepository;

/**
 * UC-02 reference data: {@link Category}. Same shape as {@link
 * AuthorService} — staff-only maintenance of the list {@link
 * BookService#categoryOptions()} and {@link BookService#categoryFilterOptions()}
 * draw from for the Book form's checkboxes and the public search filter.
 */
@Service
@Transactional
public class CategoryService {

    private final CategoryRepository categories;

    public CategoryService(CategoryRepository categories) {
        this.categories = categories;
    }

    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    @Transactional(readOnly = true)
    public List<Category> findAll() {
        return categories.findAll(Sort.by("categoryName"));
    }

    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    @Transactional(readOnly = true)
    public Category findById(Integer id) {
        return categories.findById(id).orElseThrow(() -> new NoSuchElementException("Category not found"));
    }

    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    public Category create(CategoryForm form) {
        rejectDuplicateName(form, null);
        Category category = new Category();
        applyForm(category, form);
        return categories.save(category);
    }

    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    public Category update(Integer id, CategoryForm form) {
        Category category = findById(id);
        rejectDuplicateName(form, id);
        applyForm(category, form);
        return categories.save(category);
    }

    private void rejectDuplicateName(CategoryForm form, Integer excludingId) {
        categories.findByCategoryNameIgnoreCase(form.getCategoryName().trim())
                .filter(existing -> excludingId == null || !existing.getCategoryId().equals(excludingId))
                .ifPresent(existing -> {
                    throw new DuplicateFieldException("categoryName",
                            "\"" + form.getCategoryName() + "\" already exists.");
                });
    }

    private void applyForm(Category category, CategoryForm form) {
        category.setCategoryName(form.getCategoryName().trim());
        category.setDescription(form.getDescription() == null || form.getDescription().isBlank()
                ? null : form.getDescription().trim());
    }

    /** Same reasoning as {@link AuthorService#deactivate} — no "in use" guard needed. */
    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    public void deactivate(Integer id) {
        Category category = findById(id);
        category.setActive(false);
        categories.save(category);
    }
}
