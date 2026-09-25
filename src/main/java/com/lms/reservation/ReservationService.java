package com.lms.reservation;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.lms.common.domain.AppUserRepository;
import com.lms.common.domain.AuditLog;
import com.lms.common.domain.AuditLogRepository;
import com.lms.common.domain.Book;
import com.lms.common.domain.BookCopyRepository;
import com.lms.common.domain.BookCopyStatus;
import com.lms.common.domain.BookRepository;
import com.lms.common.domain.Member;
import com.lms.common.domain.MemberRepository;
import com.lms.common.domain.NotificationFactory;
import com.lms.common.domain.NotificationRepository;
import com.lms.common.domain.Reservation;
import com.lms.common.domain.ReservationRepository;
import com.lms.common.domain.ReservationStatus;
import com.lms.common.domain.SystemSettingRepository;
import com.lms.common.event.LoanReturnedEvent;
import com.lms.reservation.dto.MyReservationRow;
import com.lms.reservation.dto.QueueGroupView;
import com.lms.reservation.dto.QueueRowView;
import com.lms.reservation.dto.ReservationPanelView;

/**
 * UC-04 — place a reservation, "My Reservations", and the staff side of the
 * waiting list: mark the next member Ready, confirm collection (Fulfilled),
 * and expire an uncollected hold. Everything here comes straight from the
 * UC-04 scenario in {@code docs/scenarios.pdf} and business-rules.md §3;
 * nothing below is a made-up rule.
 *
 * <p>The queue is per title, FIFO by {@code RequestedAt}, and queue
 * position is never stored (R12) — {@link #queuePositionFor} recomputes it
 * with the exact formula {@code ReservationRepository}'s own javadoc
 * documents: one more than the number of {@code Waiting} reservations for
 * the same book requested before this one.
 *
 * <p><b>Deliberate scope limit — no copy is ever set aside in the
 * database.</b> {@code BookCopy.Status}'s own schema comment
 * (01_schema.sql, "3. CIRCULATION") names a future [D5] trigger on
 * {@code Reservation} as the intended writer of the {@code 'On Hold'} copy
 * status. That trigger needs to know *which* physical copy is being held,
 * but {@code Reservation} has no {@code CopyID} column at all — by design,
 * per the entity's own javadoc ("the queue is per title, not per copy") —
 * so there is no schema-legal place to record that fact without adding a
 * column, which this task does not do. {@link #markReady} therefore only
 * checks an available copy exists before promising one; the librarian is
 * still the one who physically sets a copy aside on the shelf, the same
 * way {@link #panelFor} already trusts the librarian for issuing at the
 * desk. Flagged here, not silently worked around.
 */
@Service
@Transactional
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private static final List<ReservationStatus> ACTIVE_STATUSES = List.of(ReservationStatus.Waiting, ReservationStatus.Ready);
    private static final String STAFF_ROLES = "hasAuthority('Librarian') or hasAuthority('Library Administrator')";

    private final ReservationRepository reservations;
    private final BookRepository books;
    private final BookCopyRepository copies;
    private final MemberRepository members;
    private final NotificationRepository notifications;
    private final NotificationFactory notificationFactory;
    private final SystemSettingRepository settings;
    private final AppUserRepository appUsers;
    private final AuditLogRepository auditLogs;
    private final ObjectMapper objectMapper;

    public ReservationService(ReservationRepository reservations, BookRepository books, BookCopyRepository copies,
            MemberRepository members, NotificationRepository notifications, NotificationFactory notificationFactory,
            SystemSettingRepository settings, AppUserRepository appUsers, AuditLogRepository auditLogs,
            ObjectMapper objectMapper) {
        this.reservations = reservations;
        this.books = books;
        this.copies = copies;
        this.members = members;
        this.notifications = notifications;
        this.notificationFactory = notificationFactory;
        this.settings = settings;
        this.appUsers = appUsers;
        this.auditLogs = auditLogs;
        this.objectMapper = objectMapper;
    }

    /**
     * OBSERVER: reacts to {@link LoanReturnedEvent} instead of the return
     * flow reaching into reservations itself. Read-only by design — marking
     * the next reservation Ready stays the librarian's own manual decision
     * on the Reservation Queue screen ({@link #markReady}); this only
     * surfaces, at the moment a copy frees up, that a promotion is now
     * possible.
     */
    @EventListener
    public void onLoanReturned(LoanReturnedEvent event) {
        if (event.damaged()) {
            return;
        }
        reservations.findFirstByBookBookIdAndStatusOrderByRequestedAtAsc(event.bookId(), ReservationStatus.Waiting)
                .ifPresent(next -> log.info(
                        "Copy {} of book {} is available again; {} is next in the reservation queue (waiting since {}) and can now be marked Ready.",
                        event.copyId(), event.bookId(), next.getMember().getMembershipNo(), next.getRequestedAt()));
    }

    /**
     * Called directly from {@code catalogue/book-detail.html} as {@code
     * @reservationService.panelFor(book.bookId, currentUserId)} — see
     * {@link ReservationPanelView}'s javadoc for why a template calls
     * straight into this bean rather than {@code BookController} passing
     * the same information down: it keeps this task's whole footprint on
     * {@code com.lms.catalogue} to that one template, with no Java file in
     * that package touched at all. {@code userId} is the signed-in
     * AppUser's id (or null for a guest) — {@code CurrentUserAdvice}'s
     * {@code currentUserId} — resolved to a {@code Member} here the same
     * way every controller in this codebase already does.
     */
    @Transactional(readOnly = true)
    public ReservationPanelView panelFor(Integer bookId, Integer userId) {
        boolean hasAvailableCopy = copies.existsByBookBookIdAndStatusAndReferenceOnlyFalse(bookId, BookCopyStatus.Available);
        if (userId == null) {
            return new ReservationPanelView(hasAvailableCopy, false, null);
        }
        Optional<Member> member = members.findByUserUserId(userId);
        if (member.isEmpty()) {
            return new ReservationPanelView(hasAvailableCopy, false, null);
        }
        Optional<Reservation> active = activeReservation(bookId, member.get().getMemberId());
        if (active.isEmpty()) {
            return new ReservationPanelView(hasAvailableCopy, false, null);
        }
        Reservation r = active.get();
        String message = r.getStatus() == ReservationStatus.Ready
                ? "A copy is being held for you — collect it before the hold expires."
                : "You are number " + queuePositionFor(r) + " in the queue for this title.";
        return new ReservationPanelView(hasAvailableCopy, true, message);
    }

    private Optional<Reservation> activeReservation(Integer bookId, Integer memberId) {
        return reservations.findByMemberMemberIdOrderByRequestedAtDesc(memberId).stream()
                .filter(r -> r.getBook().getBookId().equals(bookId))
                .filter(r -> ACTIVE_STATUSES.contains(r.getStatus()))
                .findFirst();
    }

    /**
     * This task's own two refusal cases, in the order it lists them: a
     * copy is actually available ("suggest borrowing instead"), or the
     * member already has an active reservation on this title.
     */
    @PreAuthorize("isAuthenticated()")
    public Reservation reserve(Integer bookId, Integer memberId) {
        Book book = books.findById(bookId).orElseThrow(() -> new NoSuchElementException("Book not found"));
        Member member = members.findById(memberId).orElseThrow(() -> new NoSuchElementException("Member not found"));

        if (copies.existsByBookBookIdAndStatusAndReferenceOnlyFalse(bookId, BookCopyStatus.Available)) {
            throw new ReservationException(
                    "A copy of \"" + book.getTitle() + "\" is available right now — borrow it instead of reserving.");
        }
        if (reservations.existsByBookBookIdAndMemberMemberIdAndStatusIn(bookId, memberId, ACTIVE_STATUSES)) {
            throw new ReservationException("You already have an active reservation for this title.");
        }

        Reservation reservation = new Reservation();
        reservation.setBook(book);
        reservation.setMember(member);
        // RequestedAt is set by @PrePersist; Status defaults to Waiting.
        return reservations.save(reservation);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<MyReservationRow> myReservations(Integer memberId) {
        return reservations.findByMemberMemberIdOrderByRequestedAtDesc(memberId).stream()
                .map(r -> new MyReservationRow(r.getReservationId(), r.getBook().getTitle(), r.getRequestedAt(),
                        queuePositionFor(r), r.getStatus().name(), r.getExpiresAt(), isCancellable(r)))
                .toList();
    }

    /** R12's exact formula. Null (not zero) for anything not Waiting — "position" is meaningless once you are not in line. */
    private Integer queuePositionFor(Reservation r) {
        if (r.getStatus() != ReservationStatus.Waiting) {
            return null;
        }
        long before = reservations.countByBookBookIdAndStatusAndRequestedAtBefore(
                r.getBook().getBookId(), ReservationStatus.Waiting, r.getRequestedAt());
        return (int) before + 1;
    }

    private static boolean isCancellable(Reservation r) {
        return r.getStatus() == ReservationStatus.Waiting || r.getStatus() == ReservationStatus.Ready;
    }

    /**
     * Ownership is checked the same way a stale id is — {@link
     * NoSuchElementException}, not a distinct "not yours" error — so a
     * member probing another member's reservation id learns nothing more
     * than if it did not exist at all.
     */
    @PreAuthorize("isAuthenticated()")
    public void cancel(Integer reservationId, Integer memberId) {
        Reservation reservation = reservations.findById(reservationId)
                .filter(r -> r.getMember().getMemberId().equals(memberId))
                .orElseThrow(() -> new NoSuchElementException("Reservation not found"));
        if (!isCancellable(reservation)) {
            throw new ReservationException("This reservation can no longer be cancelled.");
        }
        reservation.setStatus(ReservationStatus.Cancelled);
        reservation.setClosedAt(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        reservations.save(reservation);
    }

    // ---- Staff: Reservation Queue ----

    /**
     * Every open reservation (Waiting or Ready), grouped by title in queue
     * order — the UC-04 goal statement's own words, "manage the
     * reservation waiting list fairly," is why {@link #markReady} below
     * refuses to jump the queue rather than just trusting whichever row's
     * button the librarian happened to click.
     */
    @PreAuthorize(STAFF_ROLES)
    @Transactional(readOnly = true)
    public List<QueueGroupView> queueForStaff() {
        List<Reservation> open = reservations.findByStatusInOrderByRequestedAtAsc(ACTIVE_STATUSES);

        // LinkedHashMap keeps each title's group in the order its first
        // (oldest-requested) row was encountered, so the queue that has
        // been waiting longest overall appears first on the page.
        Map<Integer, List<Reservation>> byBookId = new LinkedHashMap<>();
        for (Reservation r : open) {
            byBookId.computeIfAbsent(r.getBook().getBookId(), key -> new ArrayList<>()).add(r);
        }

        List<QueueGroupView> groups = new ArrayList<>();
        for (List<Reservation> group : byBookId.values()) {
            Book book = group.get(0).getBook();
            boolean copyAvailable = copies.existsByBookBookIdAndStatusAndReferenceOnlyFalse(book.getBookId(), BookCopyStatus.Available);

            List<QueueRowView> rows = new ArrayList<>();
            for (Reservation r : group) {
                Integer position = queuePositionFor(r);
                boolean canMarkReady = r.getStatus() == ReservationStatus.Waiting && position != null && position == 1 && copyAvailable;
                boolean canFulfill = r.getStatus() == ReservationStatus.Ready;
                boolean canExpire = r.getStatus() == ReservationStatus.Ready;
                String memberName = r.getMember().getUser().getFirstName() + " " + r.getMember().getUser().getLastName();
                rows.add(new QueueRowView(r.getReservationId(), memberName, r.getRequestedAt(), position,
                        r.getStatus().name(), r.getExpiresAt(), canMarkReady, canFulfill, canExpire));
            }
            groups.add(new QueueGroupView(book.getBookId(), book.getTitle(), rows));
        }
        return groups;
    }

    /**
     * "When a copy becomes available, the librarian marks the next member
     * in the queue as Ready." Both refusal checks below re-verify what
     * {@link #queueForStaff} already used to decide whether to show this
     * row's button at all, so a direct POST to a hidden action still can't
     * skip either one.
     */
    @PreAuthorize(STAFF_ROLES)
    public void markReady(Integer reservationId) {
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new NoSuchElementException("Reservation not found"));
        if (reservation.getStatus() != ReservationStatus.Waiting) {
            throw new ReservationException("Only a waiting reservation can be marked ready.");
        }
        Integer position = queuePositionFor(reservation);
        if (position == null || position != 1) {
            throw new ReservationException("Only the next member in the queue can be marked ready.");
        }
        Book book = reservation.getBook();
        if (!copies.existsByBookBookIdAndStatusAndReferenceOnlyFalse(book.getBookId(), BookCopyStatus.Available)) {
            throw new ReservationException("No available copy of \"" + book.getTitle() + "\" to hold yet.");
        }

        LocalDateTime readyAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime expiresAt = readyAt.plusDays(holdDays());
        reservation.setReadyAt(readyAt);
        reservation.setExpiresAt(expiresAt);
        reservation.setStatus(ReservationStatus.Ready);
        reservations.save(reservation);

        notifications.save(notificationFactory.reservationReady(reservation.getMember().getUser(), book.getTitle(), expiresAt));
    }

    /** "The librarian confirms allocation when the member collects: status becomes Fulfilled." */
    @PreAuthorize(STAFF_ROLES)
    public void fulfill(Integer reservationId) {
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new NoSuchElementException("Reservation not found"));
        if (reservation.getStatus() != ReservationStatus.Ready) {
            throw new ReservationException("Only a ready reservation can be confirmed as collected.");
        }
        reservation.setStatus(ReservationStatus.Fulfilled);
        reservation.setClosedAt(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        reservations.save(reservation);
    }

    /**
     * "If a Ready reservation passes its ExpiresAt without collection: the
     * librarian expires it, and the next member in the queue becomes
     * eligible." That second half needs no code of its own — queue
     * position is never stored (R12), so the moment this row's status
     * stops being Waiting/Ready, {@link #queuePositionFor} and {@link
     * #queueForStaff}'s {@code canMarkReady} check simply see the next row
     * as position 1 on the very next read.
     */
    @PreAuthorize(STAFF_ROLES)
    public void expire(Integer reservationId) {
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new NoSuchElementException("Reservation not found"));
        if (reservation.getStatus() != ReservationStatus.Ready) {
            throw new ReservationException("Only a ready reservation can expire.");
        }
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        if (reservation.getExpiresAt() != null && reservation.getExpiresAt().isAfter(now)) {
            throw new ReservationException("This hold does not expire until " + reservation.getExpiresAt().toLocalDate() + ".");
        }
        reservation.setStatus(ReservationStatus.Expired);
        reservation.setClosedAt(now);
        reservations.save(reservation);
    }

    /**
     * "Cancel" on any open reservation (Waiting or Ready), with a required
     * reason — a librarian's own decision to pull a reservation, distinct
     * from the member's own {@link #cancel}: no ownership check (staff can
     * act on anyone's), and the reason is mandatory here where it is not
     * for a member cancelling their own.
     *
     * <p>The reason has nowhere on {@code Reservation} itself to live —
     * this task does not add a column — so it is recorded the same way
     * {@code AuditLog}'s own schema comment says a free-text justification
     * should be: as JSON in {@code AuditLog.Details} (matching
     * {@code CK_AuditLog_DetailsIsJson}), built with the {@code
     * ObjectMapper} Spring already provides rather than hand-escaped, since
     * a reason is free text a staff member typed and could contain a quote
     * or a backslash. Written directly here rather than via {@code
     * @AuditAction} — that annotation's aspect has no way to carry a
     * reason, and using both would double the audit row. The member is
     * also told why, the same way {@link #markReady} already notifies them
     * of a hold — {@link NotificationFactory#reservationCancelledByStaff},
     * the General type being the one this schema offers with no more
     * specific fit.
     */
    @PreAuthorize(STAFF_ROLES)
    public void cancelByStaff(Integer reservationId, String reason, Integer staffUserId) {
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new NoSuchElementException("Reservation not found"));
        if (!isCancellable(reservation)) {
            throw new ReservationException("This reservation is already closed.");
        }
        String trimmedReason = reason == null ? "" : reason.trim();
        if (trimmedReason.isEmpty()) {
            throw new ReservationException("A reason is required to cancel a reservation.");
        }

        Book book = reservation.getBook();
        reservation.setStatus(ReservationStatus.Cancelled);
        reservation.setClosedAt(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        reservations.save(reservation);

        notifications.save(notificationFactory.reservationCancelledByStaff(reservation.getMember().getUser(), book.getTitle(), trimmedReason));

        AuditLog audit = new AuditLog();
        audit.setUser(appUsers.getReferenceById(staffUserId));
        audit.setActionName("CANCEL");
        audit.setEntityName("Reservation");
        audit.setEntityId(reservationId.toString());
        audit.setDetails(reasonAsJson(trimmedReason));
        auditLogs.save(audit);
    }

    private String reasonAsJson(String reason) {
        try {
            return objectMapper.writeValueAsString(Map.of("reason", reason));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise the cancellation reason as JSON", e);
        }
    }

    /** business-rules.md §3: "the member has 3 days to collect it." Read from SystemSetting per CLAUDE.md rule 9. */
    private int holdDays() {
        return settings.findById("Reservation.HoldDays")
                .map(s -> Integer.parseInt(s.getSettingValue()))
                .orElse(3);
    }
}
