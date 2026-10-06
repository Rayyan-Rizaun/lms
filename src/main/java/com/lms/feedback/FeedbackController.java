package com.lms.feedback;

import java.util.NoSuchElementException;
import java.util.Optional;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.lms.common.domain.Member;
import com.lms.common.domain.MemberFeedback;
import com.lms.common.domain.MemberRepository;
import com.lms.common.security.AppUserPrincipal;
import com.lms.feedback.dto.FeedbackForm;

/**
 * UC-10 — the member side: submit, "My Feedback", the detail page, edit,
 * and withdraw. Every route requires a signed-in user with a {@link
 * Member} row (the same "is this AppUser also a member" lookup {@code
 * AccountService} already uses) — a pure staff account with no membership
 * is redirected with an explanatory toast rather than hitting a stack
 * trace on a null member. The staff review side is {@link
 * FeedbackReviewController}, a separate controller because it redirects
 * to a different "home" page on a stale id.
 */
@Controller
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final MemberRepository members;

    public FeedbackController(FeedbackService feedbackService, MemberRepository members) {
        this.feedbackService = feedbackService;
        this.members = members;
    }

    @GetMapping("/feedback")
    public String myFeedback(@AuthenticationPrincipal AppUserPrincipal principal, Model model,
            RedirectAttributes redirectAttributes) {
        Optional<Member> member = members.findByUserUserId(principal.userId());
        if (member.isEmpty()) {
            return membersOnly(redirectAttributes);
        }
        model.addAttribute("pageTitle", "My Feedback");
        model.addAttribute("rows", feedbackService.myFeedback(member.get().getMemberId()));
        return "feedback/my-feedback";
    }

    @GetMapping("/feedback/new")
    public String newForm(@AuthenticationPrincipal AppUserPrincipal principal, Model model,
            RedirectAttributes redirectAttributes) {
        if (members.findByUserUserId(principal.userId()).isEmpty()) {
            return membersOnly(redirectAttributes);
        }
        if (!model.containsAttribute("feedbackForm")) {
            model.addAttribute("feedbackForm", new FeedbackForm());
        }
        addFormReferenceData(model);
        model.addAttribute("pageTitle", "Submit Feedback");
        model.addAttribute("formAction", "/feedback");
        return "feedback/form";
    }

    @PostMapping("/feedback")
    public String submit(@Valid @ModelAttribute("feedbackForm") FeedbackForm form, BindingResult bindingResult,
            @AuthenticationPrincipal AppUserPrincipal principal, Model model,
            RedirectAttributes redirectAttributes) {
        Optional<Member> member = members.findByUserUserId(principal.userId());
        if (member.isEmpty()) {
            return membersOnly(redirectAttributes);
        }
        if (bindingResult.hasErrors()) {
            addFormReferenceData(model);
            model.addAttribute("pageTitle", "Submit Feedback");
            model.addAttribute("formAction", "/feedback");
            return "feedback/form";
        }

        MemberFeedback feedback = feedbackService.submit(member.get().getMemberId(), form);
        redirectAttributes.addFlashAttribute("flashSuccessTitle", "Feedback submitted");
        redirectAttributes.addFlashAttribute("flashSuccessMessage",
                "Reference " + feedback.getFeedbackReference() + ". We will review it soon.");
        return "redirect:/feedback";
    }

    @GetMapping("/feedback/{id}")
    public String detail(@PathVariable Integer id, @AuthenticationPrincipal AppUserPrincipal principal, Model model,
            RedirectAttributes redirectAttributes) {
        Optional<Member> member = members.findByUserUserId(principal.userId());
        if (member.isEmpty()) {
            return membersOnly(redirectAttributes);
        }
        model.addAttribute("pageTitle", "Feedback detail");
        model.addAttribute("feedback", feedbackService.detail(member.get().getMemberId(), id));
        return "feedback/detail";
    }

    @GetMapping("/feedback/{id}/edit")
    public String editForm(@PathVariable Integer id, @AuthenticationPrincipal AppUserPrincipal principal, Model model,
            RedirectAttributes redirectAttributes) {
        Optional<Member> member = members.findByUserUserId(principal.userId());
        if (member.isEmpty()) {
            return membersOnly(redirectAttributes);
        }
        try {
            if (!model.containsAttribute("feedbackForm")) {
                model.addAttribute("feedbackForm", feedbackService.forEdit(member.get().getMemberId(), id));
            }
        } catch (FeedbackException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot edit this feedback");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
            return "redirect:/feedback/" + id;
        }
        addFormReferenceData(model);
        model.addAttribute("pageTitle", "Edit Feedback");
        model.addAttribute("formAction", "/feedback/" + id + "/edit");
        return "feedback/form";
    }

    @PostMapping("/feedback/{id}/edit")
    public String edit(@PathVariable Integer id, @Valid @ModelAttribute("feedbackForm") FeedbackForm form,
            BindingResult bindingResult, @AuthenticationPrincipal AppUserPrincipal principal, Model model,
            RedirectAttributes redirectAttributes) {
        Optional<Member> member = members.findByUserUserId(principal.userId());
        if (member.isEmpty()) {
            return membersOnly(redirectAttributes);
        }
        if (bindingResult.hasErrors()) {
            addFormReferenceData(model);
            model.addAttribute("pageTitle", "Edit Feedback");
            model.addAttribute("formAction", "/feedback/" + id + "/edit");
            return "feedback/form";
        }
        try {
            feedbackService.edit(member.get().getMemberId(), id, form);
        } catch (FeedbackException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot save this change");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
            return "redirect:/feedback/" + id;
        }
        redirectAttributes.addFlashAttribute("flashSuccessTitle", "Feedback updated");
        return "redirect:/feedback/" + id;
    }

    @PostMapping("/feedback/{id}/withdraw")
    public String withdraw(@PathVariable Integer id, @AuthenticationPrincipal AppUserPrincipal principal,
            RedirectAttributes redirectAttributes) {
        Optional<Member> member = members.findByUserUserId(principal.userId());
        if (member.isEmpty()) {
            return membersOnly(redirectAttributes);
        }
        try {
            feedbackService.withdraw(member.get().getMemberId(), id);
            redirectAttributes.addFlashAttribute("flashSuccessTitle", "Feedback withdrawn");
        } catch (FeedbackException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot withdraw this feedback");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
        }
        return "redirect:/feedback";
    }

    private void addFormReferenceData(Model model) {
        model.addAttribute("categoryOptions", feedbackService.categoryOptions());
        model.addAttribute("priorityOptions", FeedbackService.priorityOptions());
    }

    private String membersOnly(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("flashErrorTitle", "Members only");
        redirectAttributes.addFlashAttribute("flashErrorMessage", "Only a Library Member or Academic Staff Member account can use feedback.");
        return "redirect:/";
    }

    @ExceptionHandler(NoSuchElementException.class)
    public String notFound(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("flashErrorTitle", "Not found");
        redirectAttributes.addFlashAttribute("flashErrorMessage", "That feedback or category may no longer exist.");
        return "redirect:/feedback";
    }
}
