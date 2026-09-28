package com.lms.borrowing;

import java.util.List;
import java.util.NoSuchElementException;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.lms.borrowing.dto.IssueLoanForm;
import com.lms.borrowing.dto.LoanHistoryRow;
import com.lms.borrowing.dto.ReturnLoanForm;
import com.lms.borrowing.dto.ReturnResult;
import com.lms.common.domain.Loan;
import com.lms.common.domain.LoanRenewal;
import com.lms.common.security.AppUserPrincipal;

/**
 * UC-03 — issue, return, Current Loans and Loan History (not renewals, the
 * rest of "Borrowing, Returns &amp; Renewals," still out of this task's
 * scope). The issue flow ({@link #issueForm}/{@link #issue}) is untouched
 * from the earlier pass. Staff-only throughout: neither screen calls a
 * method {@code searchMembers}/{@code searchCopies}/{@code loanHistory}
 * etc. are read-only and carry no {@code @PreAuthorize} of their own, so —
 * the same gap already found and fixed once in {@code com.lms.catalogue}
 * — the GET handlers below are annotated directly, rather than relying on
 * {@link BorrowingService#issue}/{@link BorrowingService#returnBook}'s own
 * checks alone to keep a signed-in, non-staff member from even viewing the
 * desk screens.
 */
@Controller
public class LoanController {

    private static final String STAFF_ROLES = "hasAuthority('Librarian') or hasAuthority('Library Administrator')";

    private final BorrowingService borrowingService;

    public LoanController(BorrowingService borrowingService) {
        this.borrowingService = borrowingService;
    }

    /**
     * UC-03 main flow: search for and select a member, search for and
     * select a copy, then confirm. Every step is a plain GET with query
     * parameters (this project has no JS framework for a live wizard) —
     * selecting a member or a copy re-requests this same page with
     * {@code memberId}/{@code copyId} added, so both stay selected while
     * the librarian keeps searching for the other one.
     */
    @PreAuthorize(STAFF_ROLES)
    @GetMapping("/borrowing/issue")
    public String issueForm(@RequestParam(required = false) String memberQuery,
            @RequestParam(required = false) Integer memberId,
            @RequestParam(required = false) String copyQuery,
            @RequestParam(required = false) Integer copyId,
            Model model) {

        model.addAttribute("pageTitle", "Issue Book");
        model.addAttribute("memberQuery", memberQuery);
        model.addAttribute("copyQuery", copyQuery);

        model.addAttribute("memberResults",
                memberQuery != null && !memberQuery.isBlank() ? borrowingService.searchMembers(memberQuery) : List.of());
        model.addAttribute("selectedMember", memberId != null ? borrowingService.eligibility(memberId) : null);

        model.addAttribute("copyResults",
                copyQuery != null && !copyQuery.isBlank() ? borrowingService.searchCopies(copyQuery) : List.of());
        model.addAttribute("selectedCopy", copyId != null ? borrowingService.copyCandidate(copyId) : null);

        model.addAttribute("issueForm", new IssueLoanForm(memberId, copyId));
        return "borrowing/issue";
    }

    @PostMapping("/borrowing/issue")
    public String issue(@Valid @ModelAttribute("issueForm") IssueLoanForm form, BindingResult bindingResult,
            @AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Select a member and a copy first");
            redirectAttributes.addFlashAttribute("flashErrorMessage",
                    "Search for and select both before confirming the loan.");
            return "redirect:/borrowing/issue";
        }

        try {
            Loan loan = borrowingService.issue(form.getMemberId(), form.getCopyId(), principal.userId());
            redirectAttributes.addFlashAttribute("flashSuccessTitle", "Book issued");
            redirectAttributes.addFlashAttribute("flashSuccessMessage",
                    "Due back " + loan.getDueAt().toLocalDate() + ".");
            return "redirect:/borrowing/loans";
        } catch (BorrowingRuleException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot issue this book");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
            return "redirect:/borrowing/issue?memberId=" + form.getMemberId() + "&copyId=" + form.getCopyId();
        }
    }

    /** A simple list of every current (Active) loan — member, book, borrowed date, due date, overdue status, and a Return action. */
    @PreAuthorize(STAFF_ROLES)
    @GetMapping("/borrowing/loans")
    public String currentLoans(Model model) {
        model.addAttribute("pageTitle", "Current Loans");
        model.addAttribute("loans", borrowingService.currentLoans());
        model.addAttribute("returnConditionOptions", BorrowingService.returnConditionOptions());
        return "borrowing/loans";
    }

    /**
     * The return modal's own submit (see {@code borrowing/loans.html} for
     * why it is a hand-built modal, not {@code components/confirm-dialog}
     * — it needs the return-condition field, which that fragment has no
     * room for). Redirects back to Current Loans either way, the same
     * "action lives on the list it acts on" shape {@code
     * com.lms.reservation}'s queue actions already use.
     */
    @PreAuthorize(STAFF_ROLES)
    @PostMapping("/borrowing/loans/{id}/return")
    public String returnLoan(@PathVariable Integer id, @Valid @ModelAttribute("returnForm") ReturnLoanForm form,
            BindingResult bindingResult, @AuthenticationPrincipal AppUserPrincipal principal,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot return this book");
            redirectAttributes.addFlashAttribute("flashErrorMessage", "Select the condition the book was returned in.");
            return "redirect:/borrowing/loans";
        }
        try {
            ReturnResult result = borrowingService.returnBook(id, form.getReturnCondition(), principal.userId());
            StringBuilder message = new StringBuilder("\"" + result.bookTitle() + "\" returned.");
            message.append(result.fineRaised()
                    ? " An overdue fine of LKR " + result.fineAmount() + " was raised."
                    : " No fine was raised.");
            if (result.damaged()) {
                message.append(" Recorded as damaged; the copy is out of circulation.");
            }
            redirectAttributes.addFlashAttribute("flashSuccessTitle", "Book returned");
            redirectAttributes.addFlashAttribute("flashSuccessMessage", message.toString());
        } catch (BorrowingRuleException | NoSuchElementException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot return this book");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
        }
        return "redirect:/borrowing/loans";
    }

    @PreAuthorize(STAFF_ROLES)
    @PostMapping("/borrowing/loans/{id}/renew")
    public String renewLoan(@PathVariable Integer id, @AuthenticationPrincipal AppUserPrincipal principal,
            RedirectAttributes redirectAttributes) {
        try {
            LoanRenewal renewal = borrowingService.renewByStaff(id, principal.userId());
            redirectAttributes.addFlashAttribute("flashSuccessTitle", "Loan renewed");
            redirectAttributes.addFlashAttribute("flashSuccessMessage",
                    "New due date " + renewal.getNewDueAt().toLocalDate() + ".");
        } catch (BorrowingRuleException | NoSuchElementException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot renew this loan");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
        }
        return "redirect:/borrowing/loans";
    }

    /** Every Returned loan — member, book, borrowed/returned dates, days overdue, and any fine raised. Searchable, paginated. */
    @PreAuthorize(STAFF_ROLES)
    @GetMapping("/borrowing/history")
    public String history(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page, Model model) {
        Page<LoanHistoryRow> result = borrowingService.loanHistory(q, page);

        model.addAttribute("pageTitle", "Loan History");
        model.addAttribute("q", q);
        model.addAttribute("rows", result.getContent());

        long totalItems = result.getTotalElements();
        int totalPages = Math.max(result.getTotalPages(), 1);
        model.addAttribute("page", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalItems", totalItems);
        model.addAttribute("pageSize", BorrowingService.HISTORY_PAGE_SIZE);
        // Computed here, not an inline Math.min(int, long) in the template —
        // the ambiguous-overload trap already flagged in catalogue/books.html.
        model.addAttribute("firstItem", totalItems == 0 ? 0 : (long) (page - 1) * BorrowingService.HISTORY_PAGE_SIZE + 1);
        model.addAttribute("lastItem", Math.min((long) page * BorrowingService.HISTORY_PAGE_SIZE, totalItems));

        return "borrowing/history";
    }

    /** A stale member/copy id in the query string — never a stack trace. */
    @ExceptionHandler(NoSuchElementException.class)
    public String notFound(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("flashErrorTitle", "Not found");
        redirectAttributes.addFlashAttribute("flashErrorMessage", "That member or copy may no longer exist.");
        return "redirect:/borrowing/issue";
    }
}
