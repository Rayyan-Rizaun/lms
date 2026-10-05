package com.lms.fine;

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
import com.lms.common.domain.MemberRepository;
import com.lms.common.security.AppUserPrincipal;
import com.lms.fine.dto.AppealForm;

/**
 * UC-06, UC-07 — the member side: "My Fines," the fine detail page, and
 * submitting an appeal. Every route requires a signed-in user with a
 * {@link Member} row (the same "is this AppUser also a member" lookup
 * {@code AccountService} already uses) — a pure staff account with no
 * membership is redirected with an explanatory toast rather than hitting a
 * stack trace on a null member. Ownership of a fine is checked in {@link
 * FineService} itself, not just by the URL. The staff side is {@link
 * FineController}, a separate controller with its own home page on a
 * stale id.
 */
@Controller
public class MyFineController {

    private final FineService fineService;
    private final MemberRepository members;

    public MyFineController(FineService fineService, MemberRepository members) {
        this.fineService = fineService;
        this.members = members;
    }

    @GetMapping("/fines/mine")
    public String myFines(@AuthenticationPrincipal AppUserPrincipal principal, Model model,
            RedirectAttributes redirectAttributes) {
        Optional<Member> member = members.findByUserUserId(principal.userId());
        if (member.isEmpty()) {
            return membersOnly(redirectAttributes);
        }
        model.addAttribute("pageTitle", "My Fines");
        model.addAttribute("rows", fineService.myFines(member.get().getMemberId()));
        return "fine/my-fines";
    }

    @GetMapping("/fines/mine/{id}")
    public String detail(@PathVariable Integer id, @AuthenticationPrincipal AppUserPrincipal principal, Model model,
            RedirectAttributes redirectAttributes) {
        Optional<Member> member = members.findByUserUserId(principal.userId());
        if (member.isEmpty()) {
            return membersOnly(redirectAttributes);
        }
        if (!model.containsAttribute("appealForm")) {
            model.addAttribute("appealForm", new AppealForm());
        }
        model.addAttribute("pageTitle", "Fine detail");
        model.addAttribute("fine", fineService.myFineDetail(member.get().getMemberId(), id));
        return "fine/detail";
    }

    @PostMapping("/fines/mine/{id}/appeal")
    public String appeal(@PathVariable Integer id, @Valid @ModelAttribute("appealForm") AppealForm form,
            BindingResult bindingResult, @AuthenticationPrincipal AppUserPrincipal principal,
            RedirectAttributes redirectAttributes) {
        Optional<Member> member = members.findByUserUserId(principal.userId());
        if (member.isEmpty()) {
            return membersOnly(redirectAttributes);
        }
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot submit this appeal");
            redirectAttributes.addFlashAttribute("flashErrorMessage", "Enter a reason for your appeal.");
            return "redirect:/fines/mine/" + id;
        }
        try {
            fineService.submitAppeal(member.get().getMemberId(), id, form);
            redirectAttributes.addFlashAttribute("flashSuccessTitle", "Appeal submitted");
            redirectAttributes.addFlashAttribute("flashSuccessMessage", "This fine now shows Under Appeal while staff review it.");
        } catch (FineException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot submit this appeal");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
        }
        return "redirect:/fines/mine/" + id;
    }

    private String membersOnly(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("flashErrorTitle", "Members only");
        redirectAttributes.addFlashAttribute("flashErrorMessage", "Only a Library Member or Academic Staff Member account has fines.");
        return "redirect:/";
    }

    @ExceptionHandler(NoSuchElementException.class)
    public String notFound(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("flashErrorTitle", "Not found");
        redirectAttributes.addFlashAttribute("flashErrorMessage", "That fine may no longer exist.");
        return "redirect:/fines/mine";
    }
}
