package com.lms.feedback;

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

import com.lms.common.domain.MemberFeedback;
import com.lms.common.security.AppUserPrincipal;
import com.lms.common.web.DataTableColumn;
import com.lms.feedback.dto.FeedbackListRow;
import com.lms.feedback.dto.FeedbackReviewForm;

/**
 * UC-10 — the staff side: the "Feedback" list (search, filter, sort,
 * paginate) and the review screen (move status, write a response). Staff
 * cannot delete feedback at all, so there is no delete endpoint here —
 * withdrawal is a member-only action in {@link FeedbackController}. Gated
 * to Library Administrator throughout, matching UC-10's own scenario
 * ("Supporting Actor: Library Administrator", docs/scenarios.pdf) — every
 * GET below carries its own {@code @PreAuthorize} directly, not just
 * {@link FeedbackService}'s, the same gap already found and fixed in
 * several other feature packages this session.
 */
@Controller
public class FeedbackReviewController {

    private static final String STAFF_ROLE = "hasAuthority('Library Administrator')";

    private final FeedbackService feedbackService;

    public FeedbackReviewController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PreAuthorize(STAFF_ROLE)
    @GetMapping("/feedback/manage")
    public String list(@RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String priority,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "submittedAt") String sort,
            @RequestParam(defaultValue = "asc") String dir,
            Model model) {

        Page<FeedbackListRow> result = feedbackService.staffList(q, status, categoryId, priority, page, sort, dir);

        model.addAttribute("pageTitle", "Feedback");
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("priority", priority);
        model.addAttribute("statusOptions", FeedbackService.statusFilterOptions());
        model.addAttribute("categoryOptions", feedbackService.categoryFilterOptions());
        model.addAttribute("priorityOptions", FeedbackService.priorityFilterOptions());

        model.addAttribute("columns", java.util.List.of(
                DataTableColumn.left("Reference", "reference"),
                DataTableColumn.left("Member"),
                DataTableColumn.left("Subject", "subject"),
                DataTableColumn.left("Category"),
                DataTableColumn.left("Priority", "priority"),
                DataTableColumn.left("Status", "status"),
                DataTableColumn.right("Submitted", "submittedAt")));
        model.addAttribute("rows", result.getContent());
        model.addAttribute("currentSort", sort);
        model.addAttribute("currentDir", dir);

        long totalItems = result.getTotalElements();
        int totalPages = Math.max(result.getTotalPages(), 1);
        model.addAttribute("page", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalItems", totalItems);
        model.addAttribute("pageSize", FeedbackService.PAGE_SIZE);
        // Computed here, not with an inline SpEL Math.min(int, long) in the
        // template — that ambiguous-overload trap is exactly what bit
        // catalogue/books.html's own pagination block (flagged separately,
        // not this package's to fix).
        model.addAttribute("firstItem", totalItems == 0 ? 0 : (long) (page - 1) * FeedbackService.PAGE_SIZE + 1);
        model.addAttribute("lastItem", Math.min((long) page * FeedbackService.PAGE_SIZE, totalItems));

        return "feedback/manage-list";
    }

    @PreAuthorize(STAFF_ROLE)
    @GetMapping("/feedback/manage/{id}")
    public String detail(@PathVariable Integer id, Model model) {
        if (!model.containsAttribute("reviewForm")) {
            FeedbackReviewForm form = new FeedbackReviewForm();
            model.addAttribute("reviewForm", form);
        }
        model.addAttribute("pageTitle", "Review Feedback");
        model.addAttribute("feedback", feedbackService.staffDetail(id));
        model.addAttribute("statusOptions", FeedbackService.statusOptions());
        return "feedback/review";
    }

    @PreAuthorize(STAFF_ROLE)
    @PostMapping("/feedback/manage/{id}/review")
    public String review(@PathVariable Integer id, @Valid @ModelAttribute("reviewForm") FeedbackReviewForm form,
            BindingResult bindingResult, @AuthenticationPrincipal AppUserPrincipal principal,
            Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle", "Review Feedback");
            model.addAttribute("feedback", feedbackService.staffDetail(id));
            model.addAttribute("statusOptions", FeedbackService.statusOptions());
            return "feedback/review";
        }
        try {
            MemberFeedback feedback = feedbackService.review(principal.userId(), id, form);
            redirectAttributes.addFlashAttribute("flashSuccessTitle", "Feedback updated");
            redirectAttributes.addFlashAttribute("flashSuccessMessage",
                    "Reference " + feedback.getFeedbackReference() + " is now " + feedback.getStatus().dbValue() + ".");
        } catch (FeedbackException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot update this feedback");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
        }
        return "redirect:/feedback/manage/" + id;
    }

    @ExceptionHandler(NoSuchElementException.class)
    public String notFound(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("flashErrorTitle", "Not found");
        redirectAttributes.addFlashAttribute("flashErrorMessage", "That feedback may no longer exist.");
        return "redirect:/feedback/manage";
    }
}
