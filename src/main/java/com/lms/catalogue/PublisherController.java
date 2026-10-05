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

import com.lms.catalogue.dto.PublisherForm;

/**
 * UC-02 reference data: {@link com.lms.common.domain.Publisher}.
 * Staff-only throughout — a guest only ever sees a publisher's name on a
 * book's detail page.
 */
@Controller
public class PublisherController {

    private final PublisherService publisherService;

    public PublisherController(PublisherService publisherService) {
        this.publisherService = publisherService;
    }

    @GetMapping("/catalogue/publishers")
    public String list(Model model) {
        model.addAttribute("pageTitle", "Publishers");
        model.addAttribute("publishers", publisherService.findAll());
        return "catalogue/publishers";
    }

    // See BookController#newForm's comment — this handler calls no
    // @PreAuthorize-protected service method on its own.
    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    @GetMapping("/catalogue/publishers/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("publisherForm")) {
            model.addAttribute("publisherForm", new PublisherForm());
        }
        model.addAttribute("pageTitle", "Add a publisher");
        model.addAttribute("formAction", "/catalogue/publishers");
        return "catalogue/publisher-form";
    }

    @PostMapping("/catalogue/publishers")
    public String create(@Valid @ModelAttribute("publisherForm") PublisherForm form, BindingResult bindingResult,
            Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle", "Add a publisher");
            model.addAttribute("formAction", "/catalogue/publishers");
            return "catalogue/publisher-form";
        }
        try {
            publisherService.create(form);
        } catch (DuplicateFieldException e) {
            bindingResult.rejectValue(e.field(), "duplicate", e.getMessage());
            model.addAttribute("pageTitle", "Add a publisher");
            model.addAttribute("formAction", "/catalogue/publishers");
            return "catalogue/publisher-form";
        }
        redirectAttributes.addFlashAttribute("flashSuccessTitle", "Publisher added");
        redirectAttributes.addFlashAttribute("flashSuccessMessage", "\"" + form.getPublisherName() + "\" is available on the Book form.");
        return "redirect:/catalogue/publishers";
    }

    @GetMapping("/catalogue/publishers/{id}/edit")
    public String editForm(@PathVariable Integer id, Model model) {
        if (!model.containsAttribute("publisherForm")) {
            var publisher = publisherService.findById(id);
            PublisherForm form = new PublisherForm();
            form.setPublisherName(publisher.getPublisherName());
            form.setWebsite(publisher.getWebsite());
            model.addAttribute("publisherForm", form);
        }
        model.addAttribute("pageTitle", "Edit publisher");
        model.addAttribute("formAction", "/catalogue/publishers/" + id);
        return "catalogue/publisher-form";
    }

    @PostMapping("/catalogue/publishers/{id}")
    public String update(@PathVariable Integer id, @Valid @ModelAttribute("publisherForm") PublisherForm form,
            BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle", "Edit publisher");
            model.addAttribute("formAction", "/catalogue/publishers/" + id);
            return "catalogue/publisher-form";
        }
        try {
            publisherService.update(id, form);
        } catch (DuplicateFieldException e) {
            bindingResult.rejectValue(e.field(), "duplicate", e.getMessage());
            model.addAttribute("pageTitle", "Edit publisher");
            model.addAttribute("formAction", "/catalogue/publishers/" + id);
            return "catalogue/publisher-form";
        }
        redirectAttributes.addFlashAttribute("flashSuccessTitle", "Publisher updated");
        redirectAttributes.addFlashAttribute("flashSuccessMessage", "\"" + form.getPublisherName() + "\" was saved.");
        return "redirect:/catalogue/publishers";
    }

    @PostMapping("/catalogue/publishers/{id}/deactivate")
    public String deactivate(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        publisherService.deactivate(id);
        redirectAttributes.addFlashAttribute("flashSuccessTitle", "Publisher deactivated");
        redirectAttributes.addFlashAttribute("flashSuccessMessage", "No longer offered on the Book form.");
        return "redirect:/catalogue/publishers";
    }

    @ExceptionHandler(NoSuchElementException.class)
    public String notFound(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("flashErrorTitle", "Publisher not found");
        redirectAttributes.addFlashAttribute("flashErrorMessage", "That publisher may have been removed.");
        return "redirect:/catalogue/publishers";
    }
}
