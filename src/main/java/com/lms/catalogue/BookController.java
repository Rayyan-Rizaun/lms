package com.lms.catalogue;

import java.beans.PropertyEditorSupport;
import java.util.List;
import java.util.NoSuchElementException;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.lms.catalogue.dto.BookForm;
import com.lms.catalogue.dto.BookListRow;
import com.lms.common.domain.Book;
import com.lms.common.web.DataTableColumn;

/**
 * UC-02 — the {@link Book} screens: the public list/search/detail pages
 * (PB-22, PB-23 — reachable without logging in, see {@code
 * SecurityConfig}'s catalogue matchers) and the staff-only create/edit/
 * deactivate actions. Copy management lives in {@link BookCopyController};
 * authors/categories/publishers each have their own small controller.
 */
@Controller
public class BookController {

    private final BookService bookService;
    private final BookCopyService bookCopyService;

    public BookController(BookService bookService, BookCopyService bookCopyService) {
        this.bookService = bookService;
        this.bookCopyService = bookCopyService;
    }

    /**
     * {@code publisherId} binds a {@code <select>} whose "— None —" option
     * has an empty value — Spring's default Integer editor rejects "" with
     * a NumberFormatException-turned-binding-error instead of treating it
     * as "no publisher chosen". {@code allowEmpty = true} here is the
     * standard Spring MVC fix; nothing about this changes how any other
     * field binds.
     */
    @InitBinder("bookForm")
    public void initBookFormBinder(WebDataBinder binder) {
        binder.registerCustomEditor(Integer.class, "publisherId", new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue((text == null || text.isBlank()) ? null : Integer.valueOf(text));
            }
        });
    }

    /** UC-02 main flow + alternative flow ("search by title, author, or category" — extended to ISBN and keyword). */
    @GetMapping("/catalogue/books")
    public String list(@RequestParam(required = false) String q,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(defaultValue = "false") boolean availableOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "title") String sort,
            @RequestParam(defaultValue = "asc") String dir,
            Model model) {

        Page<BookListRow> result = bookService.search(q, categoryId, availableOnly, page, sort, dir);

        model.addAttribute("pageTitle", "Books");
        model.addAttribute("q", q);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("availableOnly", availableOnly);
        model.addAttribute("categoryOptions", bookService.categoryFilterOptions());

        model.addAttribute("columns", List.of(
                DataTableColumn.left("Title", "title"),
                DataTableColumn.left("Author"),
                DataTableColumn.left("ISBN-13", "isbn13"),
                DataTableColumn.left("Category"),
                DataTableColumn.right("Year", "publicationYear"),
                DataTableColumn.right("Available")));
        model.addAttribute("rows", result.getContent());
        model.addAttribute("currentSort", sort);
        model.addAttribute("currentDir", dir);

        model.addAttribute("page", page);
        model.addAttribute("totalPages", Math.max(result.getTotalPages(), 1));
        model.addAttribute("totalItems", result.getTotalElements());
        model.addAttribute("pageSize", BookService.PAGE_SIZE);

        return "catalogue/books";
    }

    /** UC-02 postcondition: "Members and guest visitors can search and view the latest book availability." */
    @GetMapping("/catalogue/books/{id}")
    public String detail(@PathVariable Integer id, Model model) {
        Book book = bookService.findById(id);
        model.addAttribute("pageTitle", book.getTitle());
        model.addAttribute("book", book);
        model.addAttribute("categoryNames", BookService.categoryNames(book));
        model.addAttribute("copies", bookCopyService.forBook(id));
        return "catalogue/book-detail";
    }

    // This GET handler builds its model from a plain "new BookForm()" and
    // static reference-data lookups — nothing it calls is itself
    // @PreAuthorize-gated the way create()/forEdit() are, so without this
    // annotation directly on the method, a signed-in-but-non-staff member
    // could view (though never successfully submit) the Add Book form.
    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    @GetMapping("/catalogue/books/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("bookForm")) {
            model.addAttribute("bookForm", new BookForm());
        }
        addFormReferenceData(model);
        model.addAttribute("pageTitle", "Add a book");
        model.addAttribute("formAction", "/catalogue/books");
        return "catalogue/book-form";
    }

    @PostMapping("/catalogue/books")
    public String create(@Valid @ModelAttribute("bookForm") BookForm form, BindingResult bindingResult,
            Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addFormReferenceData(model);
            model.addAttribute("pageTitle", "Add a book");
            model.addAttribute("formAction", "/catalogue/books");
            return "catalogue/book-form";
        }
        Book book;
        try {
            book = bookService.create(form);
        } catch (DuplicateFieldException e) {
            bindingResult.rejectValue(e.field(), "duplicate", e.getMessage());
            addFormReferenceData(model);
            model.addAttribute("pageTitle", "Add a book");
            model.addAttribute("formAction", "/catalogue/books");
            return "catalogue/book-form";
        }
        redirectAttributes.addFlashAttribute("flashSuccessTitle", "Book added");
        redirectAttributes.addFlashAttribute("flashSuccessMessage", "\"" + book.getTitle() + "\" is in the catalogue.");
        return "redirect:/catalogue/books/" + book.getBookId();
    }

    @GetMapping("/catalogue/books/{id}/edit")
    public String editForm(@PathVariable Integer id, Model model) {
        if (!model.containsAttribute("bookForm")) {
            model.addAttribute("bookForm", bookService.forEdit(id));
        }
        addFormReferenceData(model);
        model.addAttribute("pageTitle", "Edit book");
        model.addAttribute("formAction", "/catalogue/books/" + id);
        model.addAttribute("bookId", id);
        return "catalogue/book-form";
    }

    @PostMapping("/catalogue/books/{id}")
    public String update(@PathVariable Integer id, @Valid @ModelAttribute("bookForm") BookForm form,
            BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addFormReferenceData(model);
            model.addAttribute("pageTitle", "Edit book");
            model.addAttribute("formAction", "/catalogue/books/" + id);
            model.addAttribute("bookId", id);
            return "catalogue/book-form";
        }
        Book book;
        try {
            book = bookService.update(id, form);
        } catch (DuplicateFieldException e) {
            bindingResult.rejectValue(e.field(), "duplicate", e.getMessage());
            addFormReferenceData(model);
            model.addAttribute("pageTitle", "Edit book");
            model.addAttribute("formAction", "/catalogue/books/" + id);
            model.addAttribute("bookId", id);
            return "catalogue/book-form";
        }
        redirectAttributes.addFlashAttribute("flashSuccessTitle", "Book updated");
        redirectAttributes.addFlashAttribute("flashSuccessMessage", "\"" + book.getTitle() + "\" was saved.");
        return "redirect:/catalogue/books/" + book.getBookId();
    }

    /** UC-02 alternative flow: "delete a record that has no active borrowing transactions." */
    @PostMapping("/catalogue/books/{id}/deactivate")
    public String deactivate(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        try {
            bookService.deactivate(id);
            redirectAttributes.addFlashAttribute("flashSuccessTitle", "Book deactivated");
            redirectAttributes.addFlashAttribute("flashSuccessMessage", "It no longer appears in catalogue search.");
        } catch (ActiveLoanException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot deactivate");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
        }
        return "redirect:/catalogue/books/" + id;
    }

    private void addFormReferenceData(Model model) {
        model.addAttribute("authorOptions", bookService.authorOptions());
        model.addAttribute("categoryCheckboxes", bookService.categoryOptions());
        model.addAttribute("publisherOptions", bookService.publisherOptions());
    }

    /** A stale link (an old bookmark, a since-deactivated-then-removed id) — never a stack trace. */
    @ExceptionHandler(NoSuchElementException.class)
    public String bookNotFound(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("flashErrorTitle", "Book not found");
        redirectAttributes.addFlashAttribute("flashErrorMessage", "That book may have been removed.");
        return "redirect:/catalogue/books";
    }
}
