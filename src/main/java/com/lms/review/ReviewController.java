package com.lms.review;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.lms.common.domain.Member;
import com.lms.common.domain.MemberRepository;
import com.lms.common.security.AppUserPrincipal;
import com.lms.review.dto.ReviewFlagForm;
import com.lms.review.dto.ReviewForm;

@Controller
public class ReviewController {

    private final ReviewService reviewService;
    private final MemberRepository members;

    public ReviewController(ReviewService reviewService, MemberRepository members) {
        this.reviewService = reviewService;
        this.members = members;
    }

    @GetMapping("/reviews")
    public String myReviews(@AuthenticationPrincipal AppUserPrincipal principal, Model model,
            RedirectAttributes redirectAttributes) {
        Optional<Member> member = members.findByUserUserId(principal.userId());
        if (member.isEmpty()) {
            return membersOnly(redirectAttributes);
        }
        model.addAttribute("pageTitle", "My Reviews");
        model.addAttribute("rows", reviewService.myReviews(member.get().getMemberId()));
        return "review/my-reviews";
    }

    @PostMapping("/reviews")
    public String submit(@Valid @ModelAttribute("reviewForm") ReviewForm form, BindingResult bindingResult,
            @AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirectAttributes) {
        Optional<Member> member = members.findByUserUserId(principal.userId());
        if (member.isEmpty()) {
            return membersOnly(redirectAttributes);
        }
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot submit this review");
            redirectAttributes.addFlashAttribute("flashErrorMessage", "Choose a rating between 1 and 5.");
            return "redirect:/catalogue/books/" + form.getBookId();
        }
        try {
            reviewService.submit(member.get().getMemberId(), form);
            redirectAttributes.addFlashAttribute("flashSuccessTitle", "Review submitted");
            redirectAttributes.addFlashAttribute("flashSuccessMessage", "Your review is pending moderation.");
        } catch (ReviewException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot submit this review");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
        }
        return "redirect:/catalogue/books/" + form.getBookId();
    }

    @PostMapping("/reviews/{id}/edit")
    public String edit(@PathVariable Integer id, @Valid @ModelAttribute("reviewForm") ReviewForm form,
            BindingResult bindingResult, @AuthenticationPrincipal AppUserPrincipal principal,
            RedirectAttributes redirectAttributes) {
        Optional<Member> member = members.findByUserUserId(principal.userId());
        if (member.isEmpty()) {
            return membersOnly(redirectAttributes);
        }
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot save this review");
            redirectAttributes.addFlashAttribute("flashErrorMessage", "Choose a rating between 1 and 5.");
            return "redirect:/catalogue/books/" + form.getBookId();
        }
        try {
            reviewService.edit(member.get().getMemberId(), id, form);
            redirectAttributes.addFlashAttribute("flashSuccessTitle", "Review updated");
            redirectAttributes.addFlashAttribute("flashSuccessMessage", "Your review is pending moderation again.");
        } catch (ReviewException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot save this review");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
        }
        return "redirect:/catalogue/books/" + form.getBookId();
    }

    @PostMapping("/reviews/{id}/remove")
    public String remove(@PathVariable Integer id, @RequestParam Integer bookId,
            @AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirectAttributes) {
        Optional<Member> member = members.findByUserUserId(principal.userId());
        if (member.isEmpty()) {
            return membersOnly(redirectAttributes);
        }
        try {
            reviewService.remove(member.get().getMemberId(), id);
            redirectAttributes.addFlashAttribute("flashSuccessTitle", "Review removed");
        } catch (ReviewException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot remove this review");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
        }
        return "redirect:/catalogue/books/" + bookId;
    }

    @PostMapping("/reviews/{id}/flag")
    public String flag(@PathVariable Integer id, @RequestParam Integer bookId,
            @Valid @ModelAttribute("reviewFlagForm") ReviewFlagForm form, BindingResult bindingResult,
            @AuthenticationPrincipal AppUserPrincipal principal, RedirectAttributes redirectAttributes) {
        Optional<Member> member = members.findByUserUserId(principal.userId());
        if (member.isEmpty()) {
            return membersOnly(redirectAttributes);
        }
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot flag this review");
            redirectAttributes.addFlashAttribute("flashErrorMessage", "Enter a reason.");
            return "redirect:/catalogue/books/" + bookId;
        }
        try {
            reviewService.flag(member.get().getMemberId(), id, form);
            redirectAttributes.addFlashAttribute("flashSuccessTitle", "Review flagged");
            redirectAttributes.addFlashAttribute("flashSuccessMessage", "It has been hidden pending a decision.");
        } catch (ReviewException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot flag this review");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
        }
        return "redirect:/catalogue/books/" + bookId;
    }

    private String membersOnly(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("flashErrorTitle", "Members only");
        redirectAttributes.addFlashAttribute("flashErrorMessage", "Only a Library Member or Academic Staff Member account can review books.");
        return "redirect:/";
    }

    @ExceptionHandler(NoSuchElementException.class)
    public String notFound(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("flashErrorTitle", "Not found");
        redirectAttributes.addFlashAttribute("flashErrorMessage", "That review or book may no longer exist.");
        return "redirect:/reviews";
    }
}
