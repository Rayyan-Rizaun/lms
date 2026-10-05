package com.lms.reservation;

import java.util.NoSuchElementException;
import java.util.Optional;

import jakarta.validation.Valid;

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

import com.lms.common.domain.Member;
import com.lms.common.domain.MemberRepository;
import com.lms.common.domain.Reservation;
import com.lms.common.security.AppUserPrincipal;
import com.lms.reservation.dto.StaffCancelForm;

/**
 * UC-04 — place a reservation (from the book detail page's own form), "My
 * Reservations" for a member, and the staff "Reservation Queue" (mark
 * Ready, confirm collection, expire an uncollected hold). The member-facing
 * handlers use the same "does this AppUser have a Member row" guard {@code
 * com.lms.feedback.FeedbackController} already uses; the staff handlers are
 * gated directly with {@code @PreAuthorize} rather than only relying on
 * {@link ReservationService}'s own checks — the same gap already found and
 * fixed once in {@code com.lms.catalogue} and repeated in {@code
 * com.lms.borrowing.LoanController}.
 */
@Controller
public class ReservationController {

    private static final String STAFF_ROLES = "hasAuthority('Librarian') or hasAuthority('Library Administrator')";

    private final ReservationService reservationService;
    private final MemberRepository members;

    public ReservationController(ReservationService reservationService, MemberRepository members) {
        this.reservationService = reservationService;
        this.members = members;
    }

    @PostMapping("/reservations")
    public String reserve(@RequestParam Integer bookId, @AuthenticationPrincipal AppUserPrincipal principal,
            RedirectAttributes redirectAttributes) {
        Optional<Member> member = members.findByUserUserId(principal.userId());
        if (member.isEmpty()) {
            return membersOnly(redirectAttributes, bookId);
        }
        try {
            Reservation reservation = reservationService.reserve(bookId, member.get().getMemberId());
            redirectAttributes.addFlashAttribute("flashSuccessTitle", "Reservation placed");
            redirectAttributes.addFlashAttribute("flashSuccessMessage",
                    "We will hold the next copy returned for \"" + reservation.getBook().getTitle() + "\".");
        } catch (ReservationException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot reserve this title");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
        }
        return "redirect:/catalogue/books/" + bookId;
    }

    @GetMapping("/reservations")
    public String myReservations(@AuthenticationPrincipal AppUserPrincipal principal, Model model,
            RedirectAttributes redirectAttributes) {
        Optional<Member> member = members.findByUserUserId(principal.userId());
        if (member.isEmpty()) {
            return membersOnly(redirectAttributes, null);
        }
        model.addAttribute("pageTitle", "My Reservations");
        model.addAttribute("rows", reservationService.myReservations(member.get().getMemberId()));
        return "reservation/my-reservations";
    }

    @PostMapping("/reservations/{id}/cancel")
    public String cancel(@PathVariable Integer id, @AuthenticationPrincipal AppUserPrincipal principal,
            RedirectAttributes redirectAttributes) {
        Optional<Member> member = members.findByUserUserId(principal.userId());
        if (member.isEmpty()) {
            return membersOnly(redirectAttributes, null);
        }
        try {
            reservationService.cancel(id, member.get().getMemberId());
            redirectAttributes.addFlashAttribute("flashSuccessTitle", "Reservation cancelled");
        } catch (ReservationException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot cancel");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
        }
        return "redirect:/reservations";
    }

    private String membersOnly(RedirectAttributes redirectAttributes, Integer bookId) {
        redirectAttributes.addFlashAttribute("flashErrorTitle", "Members only");
        redirectAttributes.addFlashAttribute("flashErrorMessage", "Only a Library Member or Academic Staff Member account can reserve titles.");
        return bookId != null ? "redirect:/catalogue/books/" + bookId : "redirect:/";
    }

    @ExceptionHandler(NoSuchElementException.class)
    public String notFound(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("flashErrorTitle", "Not found");
        redirectAttributes.addFlashAttribute("flashErrorMessage", "That book or reservation may no longer exist.");
        return "redirect:/reservations";
    }

    // ---- Staff: Reservation Queue ----

    @PreAuthorize(STAFF_ROLES)
    @GetMapping("/reservations/queue")
    public String queue(Model model) {
        model.addAttribute("pageTitle", "Reservation Queue");
        model.addAttribute("groups", reservationService.queueForStaff());
        return "reservation/queue";
    }

    @PreAuthorize(STAFF_ROLES)
    @PostMapping("/reservations/{id}/ready")
    public String markReady(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        try {
            reservationService.markReady(id);
            redirectAttributes.addFlashAttribute("flashSuccessTitle", "Marked ready");
            redirectAttributes.addFlashAttribute("flashSuccessMessage", "The member has been notified to collect it.");
        } catch (ReservationException | NoSuchElementException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot mark ready");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
        }
        return "redirect:/reservations/queue";
    }

    @PreAuthorize(STAFF_ROLES)
    @PostMapping("/reservations/{id}/fulfill")
    public String fulfill(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        try {
            reservationService.fulfill(id);
            redirectAttributes.addFlashAttribute("flashSuccessTitle", "Collection confirmed");
        } catch (ReservationException | NoSuchElementException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot confirm collection");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
        }
        return "redirect:/reservations/queue";
    }

    @PreAuthorize(STAFF_ROLES)
    @PostMapping("/reservations/{id}/expire")
    public String expire(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        try {
            reservationService.expire(id);
            redirectAttributes.addFlashAttribute("flashSuccessTitle", "Hold expired");
            redirectAttributes.addFlashAttribute("flashSuccessMessage", "The next member in the queue is now eligible.");
        } catch (ReservationException | NoSuchElementException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot expire this hold");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
        }
        return "redirect:/reservations/queue";
    }

    /**
     * A different URL from the member's own {@code POST /reservations/{id}/cancel}
     * above — same path shape would collide, and the two need different
     * request bodies (this one requires a reason) and different
     * authorization (no ownership check; any staff member, any reservation).
     */
    @PreAuthorize(STAFF_ROLES)
    @PostMapping("/reservations/{id}/staff-cancel")
    public String staffCancel(@PathVariable Integer id, @Valid @ModelAttribute("cancelForm") StaffCancelForm form,
            BindingResult bindingResult, @AuthenticationPrincipal AppUserPrincipal principal,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot cancel");
            redirectAttributes.addFlashAttribute("flashErrorMessage",
                    bindingResult.getFieldError("reason") != null
                            ? bindingResult.getFieldError("reason").getDefaultMessage()
                            : "A reason is required to cancel a reservation.");
            return "redirect:/reservations/queue";
        }
        try {
            reservationService.cancelByStaff(id, form.getReason(), principal.userId());
            redirectAttributes.addFlashAttribute("flashSuccessTitle", "Reservation cancelled");
            redirectAttributes.addFlashAttribute("flashSuccessMessage", "The member has been notified.");
        } catch (ReservationException | NoSuchElementException e) {
            redirectAttributes.addFlashAttribute("flashErrorTitle", "Cannot cancel");
            redirectAttributes.addFlashAttribute("flashErrorMessage", e.getMessage());
        }
        return "redirect:/reservations/queue";
    }
}
