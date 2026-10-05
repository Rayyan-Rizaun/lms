package com.lms.catalogue;

import java.util.NoSuchElementException;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.lms.catalogue.dto.CategoryForm;

/**
 * UC-02 reference data: {@link com.lms.common.domain.Category}. Staff-only
 * throughout — a guest only ever sees category names on the public search
 * filter ({@link BookService#categoryFilterOptions()}) and a book's own
 * detail page, never this management screen.
 */
@Controller
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/catalogue/categories")
    public String list(Model model) {
        model.addAttribute("pageTitle", "Categories");
        model.addAttribute("categories", categoryService.findAll());
        return "catalogue/categories";
    }

    // See BookController#newForm's comment — this handler calls no
    // @PreAuthorize-protected service method on its own.
    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    @GetMapping("/catalogue/categories/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("categoryForm")) {
            model.addAttribute("categoryForm", new CategoryForm());
        }
        model.addAttribute("pageTitle", "Add a category");
        model.addAttribute("formAction", "/catalogue/categories");
        return "catalogue/category-form";
    }

    @PostMapping("/catalogue/categories")
    public String create(@Valid @ModelAttribute("categoryForm") CategoryForm form, BindingResult bindingResult,
            Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle", "Add a category");
            model.addAttribute("formAction", "/catalogue/categories");
            return "catalogue/category-form";
        }
        try {
            categoryService.create(form);
        } catch (DuplicateFieldException e) {
            bindingResult.rejectValue(e.field(), "duplicate", e.getMessage());
            model.addAttribute("pageTitle", "Add a category");
            model.addAttribute("formAction", "/catalogue/categories");
            return "catalogue/category-form";
        }
        redirectAttributes.addFlashAttribute("flashSuccessTitle", "Category added");
        redirectAttributes.addFlashAttribute("flashSuccessMessage", "\"" + form.getCategoryName() + "\" is available on the Book form.");
        return "redirect:/catalogue/categories";
    }

    @GetMapping("/catalogue/categories/{id}/edit")
    public String editForm(@PathVariable Integer id, Model model) {
        if (!model.containsAttribute("categoryForm")) {
            var category = categoryService.findById(id);
            CategoryForm form = new CategoryForm();
            form.setCategoryName(category.getCategoryName());
            form.setDescription(category.getDescription());
            model.addAttribute("categoryForm", form);
        }
        model.addAttribute("pageTitle", "Edit category");
        model.addAttribute("formAction", "/catalogue/categories/" + id);
        return "catalogue/category-form";
    }

    @PostMapping("/catalogue/categories/{id}")
    public String update(@PathVariable Integer id, @Valid @ModelAttribute("categoryForm") CategoryForm form,
            BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle", "Edit category");
            model.addAttribute("formAction", "/catalogue/categories/" + id);
            return "catalogue/category-form";
        }
        try {
            categoryService.update(id, form);
        } catch (DuplicateFieldException e) {
            bindingResult.rejectValue(e.field(), "duplicate", e.getMessage());
            model.addAttribute("pageTitle", "Edit category");
            model.addAttribute("formAction", "/catalogue/categories/" + id);
            return "catalogue/category-form";
        }
        redirectAttributes.addFlashAttribute("flashSuccessTitle", "Category updated");
        redirectAttributes.addFlashAttribute("flashSuccessMessage", "\"" + form.getCategoryName() + "\" was saved.");
        return "redirect:/catalogue/categories";
    }

    @PostMapping("/catalogue/categories/{id}/deactivate")
    public String deactivate(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        categoryService.deactivate(id);
        redirectAttributes.addFlashAttribute("flashSuccessTitle", "Category deactivated");
        redirectAttributes.addFlashAttribute("flashSuccessMessage", "No longer offered on the Book form or search filter.");
        return "redirect:/catalogue/categories";
    }

    @ExceptionHandler(NoSuchElementException.class)
    public String notFound(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("flashErrorTitle", "Category not found");
        redirectAttributes.addFlashAttribute("flashErrorMessage", "That category may have been removed.");
        return "redirect:/catalogue/categories";
    }
}
