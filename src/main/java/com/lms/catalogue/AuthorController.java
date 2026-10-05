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

import com.lms.catalogue.dto.AuthorForm;

/**
 * UC-02 reference data: {@link com.lms.common.domain.Author}. Staff-only
 * throughout — there is no public author page; a guest only ever sees an
 * author's name on a book's detail page.
 */
@Controller
public class AuthorController {

    private final AuthorService authorService;

    public AuthorController(AuthorService authorService) {
        this.authorService = authorService;
    }

    @GetMapping("/catalogue/authors")
    public String list(Model model) {
        model.addAttribute("pageTitle", "Authors");
        model.addAttribute("authors", authorService.findAll());
        return "catalogue/authors";
    }

    // See BookController#newForm's comment — this handler calls no
    // @PreAuthorize-protected service method on its own.
    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    @GetMapping("/catalogue/authors/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("authorForm")) {
            model.addAttribute("authorForm", new AuthorForm());
        }
        model.addAttribute("pageTitle", "Add an author");
        model.addAttribute("formAction", "/catalogue/authors");
        return "catalogue/author-form";
    }

    @PostMapping("/catalogue/authors")
    public String create(@Valid @ModelAttribute("authorForm") AuthorForm form, BindingResult bindingResult,
            Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle", "Add an author");
            model.addAttribute("formAction", "/catalogue/authors");
            return "catalogue/author-form";
        }
        try {
            authorService.create(form);
        } catch (DuplicateFieldException e) {
            bindingResult.rejectValue(e.field(), "duplicate", e.getMessage());
            model.addAttribute("pageTitle", "Add an author");
            model.addAttribute("formAction", "/catalogue/authors");
            return "catalogue/author-form";
        }
        redirectAttributes.addFlashAttribute("flashSuccessTitle", "Author added");
        redirectAttributes.addFlashAttribute("flashSuccessMessage", "\"" + form.getAuthorName() + "\" is available on the Book form.");
        return "redirect:/catalogue/authors";
    }

    @GetMapping("/catalogue/authors/{id}/edit")
    public String editForm(@PathVariable Integer id, Model model) {
        if (!model.containsAttribute("authorForm")) {
            var author = authorService.findById(id);
            AuthorForm form = new AuthorForm();
            form.setAuthorName(author.getAuthorName());
            form.setBiography(author.getBiography());
            model.addAttribute("authorForm", form);
        }
        model.addAttribute("pageTitle", "Edit author");
        model.addAttribute("formAction", "/catalogue/authors/" + id);
        return "catalogue/author-form";
    }

    @PostMapping("/catalogue/authors/{id}")
    public String update(@PathVariable Integer id, @Valid @ModelAttribute("authorForm") AuthorForm form,
            BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle", "Edit author");
            model.addAttribute("formAction", "/catalogue/authors/" + id);
            return "catalogue/author-form";
        }
        try {
            authorService.update(id, form);
        } catch (DuplicateFieldException e) {
            bindingResult.rejectValue(e.field(), "duplicate", e.getMessage());
            model.addAttribute("pageTitle", "Edit author");
            model.addAttribute("formAction", "/catalogue/authors/" + id);
            return "catalogue/author-form";
        }
        redirectAttributes.addFlashAttribute("flashSuccessTitle", "Author updated");
        redirectAttributes.addFlashAttribute("flashSuccessMessage", "\"" + form.getAuthorName() + "\" was saved.");
        return "redirect:/catalogue/authors";
    }

    @PostMapping("/catalogue/authors/{id}/deactivate")
    public String deactivate(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        authorService.deactivate(id);
        redirectAttributes.addFlashAttribute("flashSuccessTitle", "Author deactivated");
        redirectAttributes.addFlashAttribute("flashSuccessMessage", "No longer offered on the Book form.");
        return "redirect:/catalogue/authors";
    }

    @ExceptionHandler(NoSuchElementException.class)
    public String notFound(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("flashErrorTitle", "Author not found");
        redirectAttributes.addFlashAttribute("flashErrorMessage", "That author may have been removed.");
        return "redirect:/catalogue/authors";
    }
}
