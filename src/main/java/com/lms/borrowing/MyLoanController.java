package com.lms.borrowing;

import java.util.NoSuchElementException;
import java.util.Optional;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.lms.common.domain.LoanRenewal;
import com.lms.common.domain.Member;
import com.lms.common.domain.MemberRepository;
import com.lms.common.security.AppUserPrincipal;

@Controller
public class MyLoanController {

    private final BorrowingService borrowingService;
    private final MemberRepository members;

    public MyLoanController(BorrowingService borrowingService, MemberRepository members) {
        this.borrowingService = borrowingService;
        this.members = members;
    }

    @GetMapping("/borrowing/my-loans")
    public String myLoans(@AuthenticationPrincipal AppUserPrincipal principal, Model model,
            RedirectAttributes redirectAttributes) {
        Optional<Member> member = members.findByUserUserId(principal.userId());
        if (member.isEmpty()) {
            return membersOnly(redirectAttributes);
        }
        model.addAttribute("pageTitle", "My Loans");
        model.addAttribute("loans", borrowingService.myLoans(member.get().getMemberId()));
        return "borrowing/my-loans";
    }

    @PostMapping("/borrowing/my-loans/{id}/renew")
    public String renew(@PathVariable Integer id, @AuthenticationPrincipal AppUserPrincipal principal,
            RedirectAttributes redirectAttributes) {
        Optional<Member> member = members.findByUserUserId(principal.userId());
        if (member.isEmpty()) {
            return membersOnly(redirectAttributes);
        }
        try {
            LoanRenewal renewal = borrowingService.requestRenewal(member.get().getMemberId(), id, principal.userId());
            redirectAttributes.addFlashAttribute("flashSuccessTitle", "Loan renewed");
            redirectAttributes.addFlashAttribute("flashSuccessMessage",
                    "New due date " + renewal.getNewDueAt().toLocalDate() + ".");
        } catch (BorrowingRuleException | NoSuchElementException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot renew this loan");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
        }
        return "redirect:/borrowing/my-loans";
    }

    private String membersOnly(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("flashErrorTitle", "Members only");
        redirectAttributes.addFlashAttribute("flashErrorMessage", "Only a Library Member or Academic Staff Member account has loans.");
        return "redirect:/";
    }

    @ExceptionHandler(NoSuchElementException.class)
    public String notFound(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("flashErrorTitle", "Not found");
        redirectAttributes.addFlashAttribute("flashErrorMessage", "That loan may no longer exist.");
        return "redirect:/borrowing/my-loans";
    }
}
