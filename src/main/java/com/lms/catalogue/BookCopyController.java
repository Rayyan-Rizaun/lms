package com.lms.catalogue;

import java.util.List;
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

import com.lms.catalogue.dto.BookCopyForm;
import com.lms.common.domain.Book;
import com.lms.common.domain.BookCopy;
import com.lms.common.web.SelectOption;

/**
 * UC-02 inventory: create/edit/withdraw one {@link BookCopy}, always
 * nested under its {@link Book} ({@code /catalogue/books/{bookId}/copies/…})
 * so every screen here can link straight back to that book's detail page.
 * Staff-only throughout — a guest only ever sees copies read-only, on the
 * book detail page itself ({@link BookController#detail}).
 */
@Controller
public class BookCopyController {

    /** CK_BookCopy_CopyCondition. */
    private static final List<SelectOption> CONDITION_OPTIONS = List.of(
            new SelectOption("New", "New"),
            new SelectOption("Good", "Good"),
            new SelectOption("Fair", "Fair"),
            new SelectOption("Poor", "Poor"));

    /** form-field.html has no checkbox type — a Yes/No select binds to the boolean field instead. */
    private static final List<SelectOption> YES_NO_OPTIONS = List.of(
            new SelectOption("false", "No"),
            new SelectOption("true", "Yes"));

    private final BookCopyService bookCopyService;
    private final BookService bookService;

    public BookCopyController(BookCopyService bookCopyService, BookService bookService) {
        this.bookCopyService = bookCopyService;
        this.bookService = bookService;
    }

    // See BookController#newForm's comment — bookService.findById(bookId)
    // below is public (the book detail page needs it too), so without this
    // the "Add a copy" form would be equally reachable by a signed-in,
    // non-staff member.
    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    @GetMapping("/catalogue/books/{bookId}/copies/new")
    public String newForm(@PathVariable Integer bookId, Model model) {
        Book book = bookService.findById(bookId);
        if (!model.containsAttribute("copyForm")) {
            model.addAttribute("copyForm", new BookCopyForm());
        }
        addReferenceData(model);
        model.addAttribute("pageTitle", "Add a copy");
        model.addAttribute("book", book);
        model.addAttribute("formAction", "/catalogue/books/" + bookId + "/copies");
        return "catalogue/copy-form";
    }

    @PostMapping("/catalogue/books/{bookId}/copies")
    public String create(@PathVariable Integer bookId, @Valid @ModelAttribute("copyForm") BookCopyForm form,
            BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addReferenceData(model);
            model.addAttribute("pageTitle", "Add a copy");
            model.addAttribute("book", bookService.findById(bookId));
            model.addAttribute("formAction", "/catalogue/books/" + bookId + "/copies");
            return "catalogue/copy-form";
        }
        try {
            bookCopyService.create(bookId, form);
        } catch (DuplicateFieldException e) {
            bindingResult.rejectValue(e.field(), "duplicate", e.getMessage());
            addReferenceData(model);
            model.addAttribute("pageTitle", "Add a copy");
            model.addAttribute("book", bookService.findById(bookId));
            model.addAttribute("formAction", "/catalogue/books/" + bookId + "/copies");
            return "catalogue/copy-form";
        }
        redirectAttributes.addFlashAttribute("flashSuccessTitle", "Copy added");
        redirectAttributes.addFlashAttribute("flashSuccessMessage",
                "Accession " + form.getAccessionNumber() + " is now on the shelf.");
        return "redirect:/catalogue/books/" + bookId;
    }

    @GetMapping("/catalogue/copies/{copyId}/edit")
    public String editForm(@PathVariable Integer copyId, Model model) {
        BookCopy copy = bookCopyService.findById(copyId);
        if (!model.containsAttribute("copyForm")) {
            model.addAttribute("copyForm", bookCopyService.forEdit(copyId));
        }
        addReferenceData(model);
        model.addAttribute("pageTitle", "Edit copy");
        model.addAttribute("book", copy.getBook());
        model.addAttribute("copy", copy);
        model.addAttribute("formAction", "/catalogue/copies/" + copyId);
        return "catalogue/copy-form";
    }

    @PostMapping("/catalogue/copies/{copyId}")
    public String update(@PathVariable Integer copyId, @Valid @ModelAttribute("copyForm") BookCopyForm form,
            BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        BookCopy copy = bookCopyService.findById(copyId);
        if (bindingResult.hasErrors()) {
            addReferenceData(model);
            model.addAttribute("pageTitle", "Edit copy");
            model.addAttribute("book", copy.getBook());
            model.addAttribute("copy", copy);
            model.addAttribute("formAction", "/catalogue/copies/" + copyId);
            return "catalogue/copy-form";
        }
        try {
            bookCopyService.update(copyId, form);
        } catch (DuplicateFieldException e) {
            bindingResult.rejectValue(e.field(), "duplicate", e.getMessage());
            addReferenceData(model);
            model.addAttribute("pageTitle", "Edit copy");
            model.addAttribute("book", copy.getBook());
            model.addAttribute("copy", copy);
            model.addAttribute("formAction", "/catalogue/copies/" + copyId);
            return "catalogue/copy-form";
        } catch (ActiveLoanException e) {
            bindingResult.rejectValue("referenceOnly", "conflict", e.getMessage());
            addReferenceData(model);
            model.addAttribute("pageTitle", "Edit copy");
            model.addAttribute("book", copy.getBook());
            model.addAttribute("copy", copy);
            model.addAttribute("formAction", "/catalogue/copies/" + copyId);
            return "catalogue/copy-form";
        }
        redirectAttributes.addFlashAttribute("flashSuccessTitle", "Copy updated");
        redirectAttributes.addFlashAttribute("flashSuccessMessage", "Accession " + form.getAccessionNumber() + " was saved.");
        return "redirect:/catalogue/books/" + copy.getBook().getBookId();
    }

    /** UC-02's deactivate flow, applied to one copy — see BookCopyService#withdraw's javadoc. */
    @PostMapping("/catalogue/copies/{copyId}/withdraw")
    public String withdraw(@PathVariable Integer copyId, RedirectAttributes redirectAttributes) {
        BookCopy copy = bookCopyService.findById(copyId);
        Integer bookId = copy.getBook().getBookId();
        try {
            bookCopyService.withdraw(copyId);
            redirectAttributes.addFlashAttribute("flashSuccessTitle", "Copy withdrawn");
            redirectAttributes.addFlashAttribute("flashSuccessMessage",
                    "Accession " + copy.getAccessionNumber() + " is retired from circulation.");
        } catch (ActiveLoanException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot withdraw");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
        }
        return "redirect:/catalogue/books/" + bookId;
    }

    private void addReferenceData(Model model) {
        model.addAttribute("conditionOptions", CONDITION_OPTIONS);
        model.addAttribute("yesNoOptions", YES_NO_OPTIONS);
    }

    /** A stale copy id — never a stack trace. There is no "copy list" to send this back to, so the catalogue home does. */
    @ExceptionHandler(NoSuchElementException.class)
    public String notFound(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("flashErrorTitle", "Not found");
        redirectAttributes.addFlashAttribute("flashErrorMessage", "That book or copy may have been removed.");
        return "redirect:/catalogue/books";
    }
}
