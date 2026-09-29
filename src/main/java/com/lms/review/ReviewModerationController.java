package com.lms.review;

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
import com.lms.review.dto.RejectForm;
import com.lms.review.dto.ReviewQueueRow;

@Controller
public class ReviewModerationController {

    private static final String STAFF_ROLE = "hasAuthority('Library Administrator')";

    private final ReviewService reviewService;

    public ReviewModerationController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PreAuthorize(STAFF_ROLE)
    @GetMapping("/reviews/moderation")
    public String list(@RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page, Model model) {
        Page<ReviewQueueRow> result = reviewService.staffQueue(q, status, page);

        model.addAttribute("pageTitle", "Review Moderation");
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("statusOptions", ReviewService.statusFilterOptions());

        model.addAttribute("columns", List.of(
                DataTableColumn.left("Book"),
                DataTableColumn.left("Member"),
                DataTableColumn.right("Rating"),
                DataTableColumn.left("Review"),
                DataTableColumn.left("Status"),
                DataTableColumn.right("Flags"),
                DataTableColumn.right("Submitted")));
        model.addAttribute("rows", result.getContent());

        long totalItems = result.getTotalElements();
        int totalPages = Math.max(result.getTotalPages(), 1);
        model.addAttribute("page", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalItems", totalItems);
        model.addAttribute("pageSize", ReviewService.PAGE_SIZE);
        model.addAttribute("firstItem", totalItems == 0 ? 0 : (long) (page - 1) * ReviewService.PAGE_SIZE + 1);
        model.addAttribute("lastItem", Math.min((long) page * ReviewService.PAGE_SIZE, totalItems));

        return "review/moderation-list";
    }

    @PreAuthorize(STAFF_ROLE)
    @GetMapping("/reviews/moderation/{id}")
    public String detail(@PathVariable Integer id, Model model) {
        if (!model.containsAttribute("rejectForm")) {
            model.addAttribute("rejectForm", new RejectForm());
        }
        model.addAttribute("pageTitle", "Review detail");
        model.addAttribute("review", reviewService.staffDetail(id));
        return "review/moderation-detail";
    }

    @PreAuthorize(STAFF_ROLE)
    @PostMapping("/reviews/moderation/{id}/approve")
    public String approve(@PathVariable Integer id, @AuthenticationPrincipal AppUserPrincipal principal,
            RedirectAttributes redirectAttributes) {
        try {
            reviewService.approve(principal.userId(), id);
            redirectAttributes.addFlashAttribute("flashSuccessTitle", "Review approved");
            redirectAttributes.addFlashAttribute("flashSuccessMessage", "It is now visible on the book's page.");
        } catch (ReviewException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot approve this review");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
        }
        return "redirect:/reviews/moderation/" + id;
    }

    @PreAuthorize(STAFF_ROLE)
    @PostMapping("/reviews/moderation/{id}/reject")
    public String reject(@PathVariable Integer id, @Valid @ModelAttribute("rejectForm") RejectForm form,
            BindingResult bindingResult, @AuthenticationPrincipal AppUserPrincipal principal,
            Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle", "Review detail");
            model.addAttribute("review", reviewService.staffDetail(id));
            return "review/moderation-detail";
        }
        try {
            reviewService.reject(principal.userId(), id, form);
            redirectAttributes.addFlashAttribute("flashSuccessTitle", "Review rejected");
        } catch (ReviewException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot reject this review");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
        }
        return "redirect:/reviews/moderation/" + id;
    }

    @PreAuthorize(STAFF_ROLE)
    @PostMapping("/reviews/moderation/{id}/uphold")
    public String uphold(@PathVariable Integer id, @AuthenticationPrincipal AppUserPrincipal principal,
            RedirectAttributes redirectAttributes) {
        try {
            reviewService.uphold(principal.userId(), id);
            redirectAttributes.addFlashAttribute("flashSuccessTitle", "Flag upheld");
            redirectAttributes.addFlashAttribute("flashSuccessMessage", "The review has been removed.");
        } catch (ReviewException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot uphold this flag");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
        }
        return "redirect:/reviews/moderation/" + id;
    }

    @PreAuthorize(STAFF_ROLE)
    @PostMapping("/reviews/moderation/{id}/dismiss")
    public String dismiss(@PathVariable Integer id, @AuthenticationPrincipal AppUserPrincipal principal,
            RedirectAttributes redirectAttributes) {
        try {
            reviewService.dismiss(principal.userId(), id);
            redirectAttributes.addFlashAttribute("flashSuccessTitle", "Flag dismissed");
            redirectAttributes.addFlashAttribute("flashSuccessMessage", "The review is approved again.");
        } catch (ReviewException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot dismiss this flag");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
        }
        return "redirect:/reviews/moderation/" + id;
    }

    @ExceptionHandler(NoSuchElementException.class)
    public String notFound(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("flashErrorTitle", "Not found");
        redirectAttributes.addFlashAttribute("flashErrorMessage", "That review may no longer exist.");
        return "redirect:/reviews/moderation";
    }
}
