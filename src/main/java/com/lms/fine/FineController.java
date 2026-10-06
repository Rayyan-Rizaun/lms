package com.lms.fine;

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

import com.lms.common.security.AppUserPrincipal;
import com.lms.common.web.DataTableColumn;
import com.lms.common.web.StatTile;
import com.lms.fine.dto.AppealApproveForm;
import com.lms.fine.dto.AppealRejectForm;
import com.lms.fine.dto.FineListRow;
import com.lms.fine.dto.FineSummary;
import com.lms.fine.dto.PaymentForm;
import com.lms.fine.dto.ReceiptView;
import com.lms.fine.dto.ReferralForm;
import com.lms.fine.dto.WaiveForm;

/**
 * UC-05, UC-06, UC-07 — the staff side: Outstanding Fines (search, filter,
 * sort, paginate, plus the three summary tiles), record a payment, waive a
 * fine, and the appeal review queue. Staff-only throughout (Finance
 * Officer / Librarian / Library Administrator, matching {@link
 * FineService}'s own {@code @PreAuthorize} on every method here) — every
 * GET carries its own {@code @PreAuthorize} directly, the same gap already
 * found and fixed in several other feature packages this session. The
 * member side is {@link MyFineController}, a separate controller because
 * it redirects to a different "home" page on a stale id and needs its own
 * ownership guard.
 */
@Controller
public class FineController {

    private static final String STAFF_ROLES =
            "hasAuthority('Finance Officer') or hasAuthority('Librarian') or hasAuthority('Library Administrator')";

    private static final String FINANCE_ROLE = "hasAuthority('Finance Officer')";

    private final FineService fineService;

    public FineController(FineService fineService) {
        this.fineService = fineService;
    }

    @PreAuthorize(STAFF_ROLES)
    @GetMapping("/fines")
    public String outstanding(@RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "assessed") String sort,
            @RequestParam(defaultValue = "asc") String dir,
            Model model) {

        Page<FineListRow> result = fineService.staffFineList(q, status, type, page, sort, dir);

        model.addAttribute("pageTitle", "Outstanding Fines");
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("type", type);
        model.addAttribute("statusOptions", FineService.statusFilterOptions());
        model.addAttribute("typeOptions", FineService.typeFilterOptions());
        model.addAttribute("tiles", statTilesFor(fineService.fineSummary()));

        model.addAttribute("columns", List.of(
                DataTableColumn.left("Member"),
                DataTableColumn.left("Membership no."),
                DataTableColumn.left("Type", "type"),
                DataTableColumn.right("Assessed", "assessed"),
                DataTableColumn.right("Paid"),
                DataTableColumn.right("Balance"),
                DataTableColumn.left("Status", "status")));
        model.addAttribute("rows", result.getContent());
        model.addAttribute("currentSort", sort);
        model.addAttribute("currentDir", dir);

        long totalItems = result.getTotalElements();
        int totalPages = Math.max(result.getTotalPages(), 1);
        model.addAttribute("page", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalItems", totalItems);
        model.addAttribute("pageSize", FineService.PAGE_SIZE);
        // Computed here, not an inline Math.min(int, long) in the template —
        // the ambiguous-overload trap already flagged in catalogue/books.html.
        model.addAttribute("firstItem", totalItems == 0 ? 0 : (long) (page - 1) * FineService.PAGE_SIZE + 1);
        model.addAttribute("lastItem", Math.min((long) page * FineService.PAGE_SIZE, totalItems));

        return "fine/outstanding";
    }

    private static List<StatTile> statTilesFor(FineSummary summary) {
        return List.of(
                new StatTile("Total Outstanding", "LKR " + summary.totalOutstanding(), true),
                new StatTile("Collected This Month", "LKR " + summary.totalCollectedThisMonth(), false),
                new StatTile("Open Appeals", String.valueOf(summary.openAppeals()), summary.openAppeals() > 0));
    }

    @PreAuthorize(STAFF_ROLES)
    @GetMapping("/fines/{id}/pay")
    public String payForm(@PathVariable Integer id, Model model) {
        if (!model.containsAttribute("paymentForm")) {
            model.addAttribute("paymentForm", new PaymentForm());
        }
        model.addAttribute("pageTitle", "Record Payment");
        model.addAttribute("fine", fineService.fineDetail(id));
        model.addAttribute("methodOptions", FineService.methodOptions());
        return "fine/pay";
    }

    @PostMapping("/fines/{id}/pay")
    public String pay(@PathVariable Integer id, @Valid @ModelAttribute("paymentForm") PaymentForm form,
            BindingResult bindingResult, @AuthenticationPrincipal AppUserPrincipal principal, Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle", "Record Payment");
            model.addAttribute("fine", fineService.fineDetail(id));
            model.addAttribute("methodOptions", FineService.methodOptions());
            return "fine/pay";
        }

        try {
            ReceiptView receipt = fineService.pay(id, form, principal.userId());
            redirectAttributes.addFlashAttribute("receipt", receipt);
            return "redirect:/fines/" + id + "/receipt";
        } catch (FinePaymentException e) {
            bindingResult.rejectValue("amount", "exceedsBalance", e.getMessage());
            model.addAttribute("pageTitle", "Record Payment");
            model.addAttribute("fine", fineService.fineDetail(id));
            model.addAttribute("methodOptions", FineService.methodOptions());
            return "fine/pay";
        } catch (FineException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot record this payment");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
            return "redirect:/fines";
        }
    }

    /**
     * The receipt is shown once, via the flash attribute {@link #pay}
     * redirected here with — refreshing or revisiting this URL after that
     * flash is consumed has nothing left to show, so it sends the librarian
     * back to the list rather than rendering an empty receipt.
     */
    @PreAuthorize(STAFF_ROLES)
    @GetMapping("/fines/{id}/receipt")
    public String receipt(@PathVariable Integer id, Model model, RedirectAttributes redirectAttributes) {
        if (!model.containsAttribute("receipt")) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "No receipt to show");
            redirectAttributes.addFlashAttribute("flashErrorMessage", "That receipt has already been shown once.");
            return "redirect:/fines";
        }
        model.addAttribute("pageTitle", "Receipt");
        return "fine/receipt";
    }

    /** The Outstanding Fines row modal's own submit — a required reason, staff and timestamp recorded by {@link FineService#waive}. */
    @PreAuthorize(STAFF_ROLES)
    @PostMapping("/fines/{id}/waive")
    public String waive(@PathVariable Integer id, @Valid @ModelAttribute("waiveForm") WaiveForm form,
            BindingResult bindingResult, @AuthenticationPrincipal AppUserPrincipal principal,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot waive this fine");
            redirectAttributes.addFlashAttribute("flashErrorMessage", "A reason is required to waive a fine.");
            return "redirect:/fines";
        }
        try {
            fineService.waive(id, form.getReason(), principal.userId());
            redirectAttributes.addFlashAttribute("flashSuccessTitle", "Fine waived");
        } catch (FineException | NoSuchElementException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot waive this fine");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
        }
        return "redirect:/fines";
    }

    // ---- Appeal review queue ----

    @PreAuthorize(STAFF_ROLES)
    @GetMapping("/fines/appeals")
    public String appeals(Model model) {
        model.addAttribute("pageTitle", "Fine Appeals");
        model.addAttribute("rows", fineService.pendingAppeals());
        return "fine/appeals";
    }

    @PreAuthorize(STAFF_ROLES)
    @PostMapping("/fines/appeals/{id}/approve")
    public String approveAppeal(@PathVariable Integer id, @Valid @ModelAttribute("approveForm") AppealApproveForm form,
            BindingResult bindingResult, @AuthenticationPrincipal AppUserPrincipal principal,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot approve this appeal");
            redirectAttributes.addFlashAttribute("flashErrorMessage", "Enter a reduction amount greater than zero.");
            return "redirect:/fines/appeals";
        }
        try {
            fineService.approveAppeal(id, form, principal.userId());
            redirectAttributes.addFlashAttribute("flashSuccessTitle", "Appeal approved");
        } catch (FineException | NoSuchElementException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot approve this appeal");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
        }
        return "redirect:/fines/appeals";
    }

    @PreAuthorize(STAFF_ROLES)
    @PostMapping("/fines/appeals/{id}/reject")
    public String rejectAppeal(@PathVariable Integer id, @Valid @ModelAttribute("rejectForm") AppealRejectForm form,
            BindingResult bindingResult, @AuthenticationPrincipal AppUserPrincipal principal,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot reject this appeal");
            redirectAttributes.addFlashAttribute("flashErrorMessage", "Comments are required to reject an appeal.");
            return "redirect:/fines/appeals";
        }
        try {
            fineService.rejectAppeal(id, form, principal.userId());
            redirectAttributes.addFlashAttribute("flashSuccessTitle", "Appeal rejected");
        } catch (FineException | NoSuchElementException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot reject this appeal");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
        }
        return "redirect:/fines/appeals";
    }

    @PreAuthorize(FINANCE_ROLE)
    @PostMapping("/fines/appeals/{id}/refer")
    public String referAppeal(@PathVariable Integer id, @Valid @ModelAttribute("referralForm") ReferralForm form,
            BindingResult bindingResult, @AuthenticationPrincipal AppUserPrincipal principal,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot refer this appeal");
            redirectAttributes.addFlashAttribute("flashErrorMessage", "Enter a note explaining what needs to be corrected.");
            return "redirect:/fines/appeals";
        }
        try {
            fineService.referAppealForCorrection(id, form, principal.userId());
            redirectAttributes.addFlashAttribute("flashSuccessTitle", "Appeal referred for correction");
            redirectAttributes.addFlashAttribute("flashSuccessMessage", "It stays in the queue, flagged for an administrator to check the amount.");
        } catch (FineException | NoSuchElementException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot refer this appeal");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
        }
        return "redirect:/fines/appeals";
    }

    @ExceptionHandler(NoSuchElementException.class)
    public String notFound(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("flashErrorTitle", "Fine not found");
        redirectAttributes.addFlashAttribute("flashErrorMessage", "That fine may no longer exist.");
        return "redirect:/fines";
    }
}
