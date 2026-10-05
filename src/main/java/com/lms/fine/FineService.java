package com.lms.fine;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.common.domain.AppealStatus;
import com.lms.common.domain.BookIncident;
import com.lms.common.domain.Fine;
import com.lms.common.domain.FineAppeal;
import com.lms.common.domain.FinePayment;
import com.lms.common.domain.FineRepository;
import com.lms.common.domain.FineStatus;
import com.lms.common.domain.FineType;
import com.lms.common.domain.Loan;
import com.lms.common.domain.LoanRepository;
import com.lms.common.domain.NotificationFactory;
import com.lms.common.domain.NotificationRepository;
import com.lms.common.domain.PaymentMethod;
import com.lms.common.domain.PaymentStatus;
import com.lms.common.domain.StaffProfile;
import com.lms.common.domain.StaffProfileRepository;
import com.lms.common.event.LoanReturnedEvent;
import com.lms.common.security.AuditAction;
import com.lms.common.web.SelectOption;
import com.lms.fine.calculation.FineCalculationStrategy;
import com.lms.fine.calculation.FineChargeRequest;
import com.lms.fine.calculation.FineChargeResult;
import com.lms.fine.dto.AppealApproveForm;
import com.lms.fine.dto.AppealForm;
import com.lms.fine.dto.AppealQueueRow;
import com.lms.fine.dto.AppealRejectForm;
import com.lms.fine.dto.AppealView;
import com.lms.fine.dto.FineDetailView;
import com.lms.fine.dto.FineListRow;
import com.lms.fine.dto.FineSummary;
import com.lms.fine.dto.MyFineDetailView;
import com.lms.fine.dto.MyFineRow;
import com.lms.fine.dto.PaymentForm;
import com.lms.fine.dto.PaymentRow;
import com.lms.fine.dto.ReceiptView;
import com.lms.fine.dto.ReferralForm;

/**
 * UC-05, UC-06, UC-07 — fines, appeals and payments, both sides. {@link
 * #pay} is the earlier payment-only pass, extended here with the Under-
 * Appeal/Waived guard and the Cash-only {@code ReceivedByStaffID} rule;
 * every other staff method and the whole member side are new.
 *
 * <p><b>Why several methods write {@code Fine.Status} directly.</b> The
 * entity's own javadoc calls it "a kept status column, written only by the
 * D5 triggers (R24)" — the same phrasing {@code BookCopy.Status} uses, and
 * the same resolution already reached repeatedly across this project
 * (catalogue's withdraw, borrowing's issue and return, reservation's
 * queue): those triggers are a later project phase that does not exist
 * yet, and each transition below (a payment, a waiver, an appeal being
 * opened or decided) is squarely the feature that owns the action, not a
 * status this code has to borrow someone else's business logic to reach.
 *
 * <p><b>R18, followed literally.</b> "Balance = AmountAssessed − completed
 * payments − approved appeal reductions" has no term for Waived — a
 * waived fine's raw balance can still show as positive by this exact
 * formula. Every list here that means "still owes money"
 * ({@link #OUTSTANDING_STATUSES}) already excludes Waived regardless of
 * that number, so this never surfaces as a collectible amount; the
 * formula itself is implemented exactly as specified, not extended with
 * an unrequested "waived reduces it to zero" term.
 */
@Service
@Transactional
public class FineService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String STAFF_ROLES =
            "hasAuthority('Finance Officer') or hasAuthority('Librarian') or hasAuthority('Library Administrator')";

    private static final String FINANCE_ROLE = "hasAuthority('Finance Officer')";

    /** Rows per page for the staff Outstanding Fines list. */
    static final int PAGE_SIZE = 20;

    /** The statuses that mean "still owes money" — Outstanding Fines, My Fines' own ordering, and the summary tile all use exactly these. */
    private static final List<FineStatus> OUTSTANDING_STATUSES =
            List.of(FineStatus.Pending, FineStatus.UnderAppeal, FineStatus.PartiallyPaid);

    private final FineRepository fines;
    private final LoanRepository loans;
    private final StaffProfileRepository staffProfiles;
    private final NotificationRepository notifications;
    private final NotificationFactory notificationFactory;
    private final Map<FineType, FineCalculationStrategy> chargeStrategies;

    public FineService(FineRepository fines, LoanRepository loans, StaffProfileRepository staffProfiles,
            NotificationRepository notifications, NotificationFactory notificationFactory,
            List<FineCalculationStrategy> chargeStrategies) {
        this.fines = fines;
        this.loans = loans;
        this.staffProfiles = staffProfiles;
        this.notifications = notifications;
        this.notificationFactory = notificationFactory;
        this.chargeStrategies = chargeStrategies.stream()
                .collect(Collectors.toMap(FineCalculationStrategy::fineType, Function.identity()));
    }

    /** STRATEGY: the charge formula for each {@link FineType} lives in its own {@link FineCalculationStrategy}, selected here by type. */
    public FineChargeResult calculateCharge(FineType fineType, FineChargeRequest request) {
        FineCalculationStrategy strategy = chargeStrategies.get(fineType);
        if (strategy == null) {
            throw new IllegalStateException("No fine calculation strategy registered for " + fineType);
        }
        return strategy.calculate(request);
    }

    /**
     * OBSERVER: reacts to {@link LoanReturnedEvent} instead of {@code
     * BorrowingService.returnBook} building the Overdue fine inline. Runs
     * synchronously, in the same transaction as the return (plain {@code
     * @EventListener}, not {@code @TransactionalEventListener}), so a
     * failure here still rolls back the whole return exactly as it did
     * when this code lived inline.
     */
    @EventListener
    public void onLoanReturned(LoanReturnedEvent event) {
        if (fines.findByLoanLoanIdAndFineType(event.loanId(), FineType.Overdue).isPresent()) {
            return;
        }
        Loan loan = loans.getReferenceById(event.loanId());
        FineChargeResult charge = calculateCharge(FineType.Overdue, FineChargeRequest.forOverdueReturn(loan, event.returnedAt()));
        if (charge.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Fine fine = new Fine();
        fine.setMember(loan.getMember());
        fine.setLoan(loan);
        fine.setFineType(FineType.Overdue);
        fine.setRatePerDay(charge.ratePerDay());
        fine.setAmountAssessed(charge.amount());
        fines.save(fine);
    }

    public static List<SelectOption> methodOptions() {
        return List.of(
                new SelectOption("Cash", "Cash"),
                new SelectOption("Card", "Card"),
                new SelectOption("Online", "Online"));
    }

    public static List<SelectOption> statusFilterOptions() {
        List<SelectOption> options = new ArrayList<>();
        options.add(new SelectOption("", "All statuses"));
        for (FineStatus status : OUTSTANDING_STATUSES) {
            options.add(new SelectOption(status.dbValue(), status.dbValue()));
        }
        return options;
    }

    public static List<SelectOption> typeFilterOptions() {
        List<SelectOption> options = new ArrayList<>();
        options.add(new SelectOption("", "All types"));
        for (FineType type : FineType.values()) {
            options.add(new SelectOption(type.name(), type.name()));
        }
        return options;
    }

    // ================= STAFF: Outstanding Fines =================

    @PreAuthorize(STAFF_ROLES)
    @Transactional(readOnly = true)
    public Page<FineListRow> staffFineList(String q, String status, String type, int page, String sort, String dir) {
        String likeQuery = (q == null || q.isBlank()) ? null : "%" + q.trim().toLowerCase() + "%";
        FineStatus statusFilter = (status == null || status.isBlank()) ? null : parseStatus(status);
        FineType typeFilter = (type == null || type.isBlank()) ? null : FineType.valueOf(type);
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), PAGE_SIZE, sortFor(sort, dir));
        return fines.searchOutstanding(OUTSTANDING_STATUSES, statusFilter, typeFilter, likeQuery, pageable)
                .map(this::toRow);
    }

    /** Only real {@link Fine} columns are sortable — the member's name is a join, paid/balance are computed, not columns (same restraint as {@code BookService.sortFor}). */
    private static Sort sortFor(String sort, String dir) {
        String property = switch (sort == null ? "" : sort) {
            case "type" -> "fineType";
            case "assessed" -> "amountAssessed";
            case "status" -> "status";
            default -> "assessedAt";
        };
        return Sort.by("desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC, property);
    }

    /** Total outstanding, total collected this calendar month, and the number of open appeals — the three stat tiles above the list. */
    @PreAuthorize(STAFF_ROLES)
    @Transactional(readOnly = true)
    public FineSummary fineSummary() {
        BigDecimal totalOutstanding = fines.findByStatusIn(OUTSTANDING_STATUSES).stream()
                .map(this::balanceOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd = monthStart.plusMonths(1);
        BigDecimal collectedThisMonth = fines.findDistinctByPaymentsPaymentStatusAndPaymentsPaidAtBetween(
                        PaymentStatus.Completed, monthStart, monthEnd).stream()
                .flatMap(f -> f.getPayments().stream())
                .filter(p -> p.getPaymentStatus() == PaymentStatus.Completed
                        && !p.getPaidAt().isBefore(monthStart) && p.getPaidAt().isBefore(monthEnd))
                .map(FinePayment::getAmountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long openAppeals = fines.findDistinctByAppealsStatusOrderByAssessedAtAsc(AppealStatus.Pending).size();

        return new FineSummary(totalOutstanding, collectedThisMonth, openAppeals);
    }

    @Transactional(readOnly = true)
    public FineDetailView fineDetail(Integer fineId) {
        Fine fine = fines.findById(fineId).orElseThrow(() -> new NoSuchElementException("Fine not found"));
        FineListRow row = toRow(fine);
        return new FineDetailView(row.fineId(), row.memberName(), fine.getMember().getMembershipNo(),
                row.fineType(), row.amountAssessed(), row.amountPaid(), row.balance(), row.status());
    }

    private FineListRow toRow(Fine fine) {
        BigDecimal balance = balanceOf(fine);
        String memberName = fine.getMember().getUser().getFirstName() + " " + fine.getMember().getUser().getLastName();
        BigDecimal paid = completedPaymentsTotal(fine);
        boolean actionable = fine.getStatus() == FineStatus.Pending || fine.getStatus() == FineStatus.PartiallyPaid;
        return new FineListRow(fine.getFineId(), memberName, fine.getMember().getMembershipNo(), fine.getFineType().name(),
                fine.getAmountAssessed(), paid, balance, fine.getStatus().dbValue(), actionable);
    }

    private static FineStatus parseStatus(String dbValue) {
        for (FineStatus candidate : FineStatus.values()) {
            if (candidate.dbValue().equals(dbValue)) {
                return candidate;
            }
        }
        throw new FineException("Unrecognised status.");
    }

    /** R18: never stored. AmountAssessed − approved appeal reductions − completed payments. */
    private BigDecimal balanceOf(Fine fine) {
        BigDecimal reductions = fine.getAppeals().stream()
                .filter(a -> a.getStatus() == AppealStatus.Approved)
                .map(FineAppeal::getApprovedReduction)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return fine.getAmountAssessed().subtract(reductions).subtract(completedPaymentsTotal(fine));
    }

    private BigDecimal completedPaymentsTotal(Fine fine) {
        return fine.getPayments().stream()
                .filter(p -> p.getPaymentStatus() == PaymentStatus.Completed)
                .map(FinePayment::getAmountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Derives Pending/PartiallyPaid/FullyPaid from the current balance and payment history — used whenever a fine leaves Under Appeal (business-rules §2: "Rejection reinstates the original fine; approval reduces or waives it"). Never returns Waived or UnderAppeal — those are set only by {@link #waive} and {@link #submitAppeal} respectively. */
    private FineStatus recomputeStatus(Fine fine) {
        BigDecimal balance = balanceOf(fine);
        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            return FineStatus.FullyPaid;
        }
        return completedPaymentsTotal(fine).compareTo(BigDecimal.ZERO) > 0 ? FineStatus.PartiallyPaid : FineStatus.Pending;
    }

    // ================= STAFF: record a payment =================

    /**
     * UC-07 main flow + this task's own alternative flow: refuse while Under
     * Appeal or Waived, reject an amount larger than the outstanding
     * balance, record the payment, move the fine to Partially Paid or Fully
     * Paid, and return the receipt. Cash payments record who received them
     * (CK_FinePayment_CashHasReceiver requires it); Card/Online leave
     * {@code ReceivedByStaffID} null — this task's own words, "online
     * payments need not."
     */
    @PreAuthorize(STAFF_ROLES)
    @AuditAction(action = "PAYMENT", entity = "Fine")
    public ReceiptView pay(Integer fineId, PaymentForm form, Integer payingStaffUserId) {
        Fine fine = fines.findById(fineId).orElseThrow(() -> new NoSuchElementException("Fine not found"));
        if (fine.getStatus() == FineStatus.UnderAppeal) {
            throw new FineException("This fine is under appeal — payment is blocked until the appeal is decided.");
        }
        if (fine.getStatus() == FineStatus.Waived) {
            throw new FineException("This fine has been waived — no payment is needed.");
        }
        StaffProfile receivedBy = staffProfiles.findByUserUserId(payingStaffUserId)
                .orElseThrow(() -> new IllegalStateException("Signed-in user has no staff profile"));

        BigDecimal balance = balanceOf(fine);
        if (form.getAmount().compareTo(balance) > 0) {
            throw new FinePaymentException(
                    "That is more than the outstanding balance of LKR " + balance + ".");
        }

        PaymentMethod method = PaymentMethod.valueOf(form.getMethod());
        FinePayment payment = new FinePayment();
        payment.setFine(fine);
        payment.setReceiptNumber(generateUniqueReceiptNumber());
        payment.setAmountPaid(form.getAmount());
        payment.setPaymentMethod(method);
        // PaymentStatus stays at the entity's own default, Completed — this
        // screen only ever records a completed payment; Failed/Refunded are
        // not reachable from here.
        payment.setReceivedBy(method == PaymentMethod.Cash ? receivedBy : null);
        // Set explicitly rather than left to @PrePersist: save() below on an
        // already-managed Fine does not necessarily flush the cascaded new
        // FinePayment to the database (and so does not fire @PrePersist)
        // before this method returns — reading payment.getPaidAt() for the
        // receipt afterward came back null until this was set here.
        payment.setPaidAt(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        fine.getPayments().add(payment);

        BigDecimal newBalance = balance.subtract(form.getAmount());
        fine.setStatus(newBalance.compareTo(BigDecimal.ZERO) == 0 ? FineStatus.FullyPaid : FineStatus.PartiallyPaid);
        fines.save(fine);

        String memberName = fine.getMember().getUser().getFirstName() + " " + fine.getMember().getUser().getLastName();
        String staffName = receivedBy.getUser().getFirstName() + " " + receivedBy.getUser().getLastName();
        return new ReceiptView(payment.getReceiptNumber(), fine.getFineId(), memberName, fine.getFineType().name(),
                payment.getAmountPaid(), payment.getPaymentMethod().name(), payment.getPaidAt(),
                newBalance, fine.getStatus().dbValue(), staffName);
    }

    /** "RCT-<year>-<6 random digits>" — RegistrationService's membership-number pattern, reused for receipts. */
    private String generateUniqueReceiptNumber() {
        String year = String.valueOf(Year.now().getValue());
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = "RCT-" + year + "-" + String.format("%06d", RANDOM.nextInt(1_000_000));
            if (!fines.existsByPaymentsReceiptNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique receipt number after 10 attempts");
    }

    // ================= STAFF: waive =================

    @PreAuthorize(STAFF_ROLES)
    @AuditAction(action = "WAIVE", entity = "Fine")
    public Fine waive(Integer fineId, String reason, Integer staffUserId) {
        Fine fine = fines.findById(fineId).orElseThrow(() -> new NoSuchElementException("Fine not found"));
        if (fine.getStatus() == FineStatus.Waived) {
            throw new FineException("This fine has already been waived.");
        }
        if (fine.getStatus() == FineStatus.UnderAppeal) {
            throw new FineException("Resolve the pending appeal before waiving this fine.");
        }
        if (fine.getStatus() == FineStatus.FullyPaid) {
            throw new FineException("This fine is already fully paid — there is nothing to waive.");
        }
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty()) {
            throw new FineException("A reason is required to waive a fine.");
        }
        StaffProfile waivedBy = staffProfiles.findByUserUserId(staffUserId)
                .orElseThrow(() -> new IllegalStateException("Signed-in user has no staff profile"));

        fine.setStatus(FineStatus.Waived);
        fine.setWaiverReason(trimmed);
        fine.setWaivedBy(waivedBy);
        return fines.save(fine);
    }

    // ================= STAFF: appeal review queue =================

    @PreAuthorize(STAFF_ROLES)
    @Transactional(readOnly = true)
    public List<AppealQueueRow> pendingAppeals() {
        return fines.findDistinctByAppealsStatusOrderByAssessedAtAsc(AppealStatus.Pending).stream()
                .flatMap(fine -> fine.getAppeals().stream()
                        .filter(a -> a.getStatus() == AppealStatus.Pending)
                        .map(a -> toAppealRow(fine, a)))
                .toList();
    }

    private AppealQueueRow toAppealRow(Fine fine, FineAppeal appeal) {
        String memberName = fine.getMember().getUser().getFirstName() + " " + fine.getMember().getUser().getLastName();
        return new AppealQueueRow(appeal.getAppealId(), fine.getFineId(), memberName, fine.getFineType().name(),
                fine.getAmountAssessed(), balanceOf(fine), appeal.getAppealReason(), appeal.getSubmittedAt(),
                appeal.getDecisionComments());
    }

    /** business-rules §2: "approval reduces or waives it" — a reduction that zeroes the balance lands on Fully Paid via {@link #recomputeStatus}, not the separate Waived state (that one keeps its own required reason and stays {@link #waive}'s job alone). */
    @PreAuthorize(STAFF_ROLES)
    @AuditAction(action = "APPROVE", entity = "FineAppeal")
    public FineAppeal approveAppeal(Integer appealId, AppealApproveForm form, Integer staffUserId) {
        Fine fine = fines.findByAppealsAppealId(appealId).orElseThrow(() -> new NoSuchElementException("Appeal not found"));
        FineAppeal appeal = appealWithin(fine, appealId);
        if (appeal.getStatus() != AppealStatus.Pending) {
            throw new FineException("This appeal has already been decided.");
        }
        BigDecimal balance = balanceOf(fine);
        if (form.getReduction().compareTo(balance) > 0) {
            throw new FineException("The reduction cannot exceed the outstanding balance of LKR " + balance + ".");
        }
        StaffProfile decidedBy = staffProfiles.findByUserUserId(staffUserId)
                .orElseThrow(() -> new IllegalStateException("Signed-in user has no staff profile"));

        appeal.setStatus(AppealStatus.Approved);
        appeal.setDecidedBy(decidedBy);
        appeal.setDecidedAt(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        appeal.setApprovedReduction(form.getReduction());
        appeal.setDecisionComments(null);
        // balanceOf(fine) below now reflects this reduction — appeal is the
        // same managed instance already inside fine.getAppeals().
        fine.setStatus(recomputeStatus(fine));
        fines.save(fine);
        notifications.save(notificationFactory.appealApproved(
                fine.getMember().getUser(), fine.getFineType().name(), form.getReduction(), balanceOf(fine)));
        return appeal;
    }

    /** business-rules §2: "Rejection reinstates the original fine." */
    @PreAuthorize(STAFF_ROLES)
    @AuditAction(action = "REJECT", entity = "FineAppeal")
    public FineAppeal rejectAppeal(Integer appealId, AppealRejectForm form, Integer staffUserId) {
        Fine fine = fines.findByAppealsAppealId(appealId).orElseThrow(() -> new NoSuchElementException("Appeal not found"));
        FineAppeal appeal = appealWithin(fine, appealId);
        if (appeal.getStatus() != AppealStatus.Pending) {
            throw new FineException("This appeal has already been decided.");
        }
        StaffProfile decidedBy = staffProfiles.findByUserUserId(staffUserId)
                .orElseThrow(() -> new IllegalStateException("Signed-in user has no staff profile"));

        appeal.setStatus(AppealStatus.Rejected);
        appeal.setDecidedBy(decidedBy);
        appeal.setDecidedAt(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        appeal.setDecisionComments(form.getComments().trim());
        fine.setStatus(recomputeStatus(fine));
        fines.save(fine);
        notifications.save(notificationFactory.appealRejected(fine.getMember().getUser(), fine.getFineType().name(), balanceOf(fine)));
        return appeal;
    }

    @PreAuthorize(FINANCE_ROLE)
    @AuditAction(action = "REFER", entity = "FineAppeal")
    public FineAppeal referAppealForCorrection(Integer appealId, ReferralForm form, Integer staffUserId) {
        Fine fine = fines.findByAppealsAppealId(appealId).orElseThrow(() -> new NoSuchElementException("Appeal not found"));
        FineAppeal appeal = appealWithin(fine, appealId);
        if (appeal.getStatus() != AppealStatus.Pending) {
            throw new FineException("This appeal has already been decided.");
        }
        StaffProfile referredBy = staffProfiles.findByUserUserId(staffUserId)
                .orElseThrow(() -> new IllegalStateException("Signed-in user has no staff profile"));
        String staffName = referredBy.getUser().getFirstName() + " " + referredBy.getUser().getLastName();
        appeal.setDecisionComments("by " + staffName + ": " + form.getNote().trim());
        return appeal;
    }

    private static FineAppeal appealWithin(Fine fine, Integer appealId) {
        return fine.getAppeals().stream()
                .filter(a -> a.getAppealId().equals(appealId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Appeal not found"));
    }

    // ================= MEMBER SIDE =================

    /**
     * "My Fines" — outstanding fines first. {@link
     * com.lms.common.domain.FineRepository#findByMemberMemberIdOrderByAssessedAtDesc}
     * already orders newest-first; {@code Stream.sorted} is stable, so this
     * only reorders the outstanding/non-outstanding split and leaves each
     * side in that same newest-first order.
     */
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<MyFineRow> myFines(Integer memberId) {
        return fines.findByMemberMemberIdOrderByAssessedAtDesc(memberId).stream()
                .sorted(Comparator.comparing((Fine f) -> OUTSTANDING_STATUSES.contains(f.getStatus()) ? 0 : 1))
                .map(this::toMyFineRow)
                .toList();
    }

    private MyFineRow toMyFineRow(Fine fine) {
        return new MyFineRow(fine.getFineId(), fine.getFineType().name(), bookTitleOf(fine), fine.getAmountAssessed(),
                completedPaymentsTotal(fine), balanceOf(fine), fine.getStatus().dbValue(), fine.getAssessedAt());
    }

    private static String bookTitleOf(Fine fine) {
        return fine.getFineType() == FineType.Overdue
                ? fine.getLoan().getCopy().getBook().getTitle()
                : fine.getIncident().getCopy().getBook().getTitle();
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public MyFineDetailView myFineDetail(Integer memberId, Integer fineId) {
        return toDetailView(ownedByMember(memberId, fineId));
    }

    /**
     * Ownership is checked the same way a stale id is — {@link
     * NoSuchElementException}, not a distinct "not yours" error — so a
     * member probing another member's fine id learns nothing more than if
     * it did not exist at all. Enforced here, in the service, not by the
     * URL alone.
     */
    private Fine ownedByMember(Integer memberId, Integer fineId) {
        return fines.findById(fineId)
                .filter(f -> f.getMember().getMemberId().equals(memberId))
                .orElseThrow(() -> new NoSuchElementException("Fine not found"));
    }

    private MyFineDetailView toDetailView(Fine fine) {
        Integer daysOverdue = null;
        BigDecimal ratePerDay = null;
        BigDecimal replacementCost = null;
        String incidentDescription = null;

        if (fine.getFineType() == FineType.Overdue) {
            Loan loan = fine.getLoan();
            LocalDateTime returnedAt = loan.getReturnedAt() != null ? loan.getReturnedAt() : LocalDateTime.now();
            daysOverdue = (int) Math.max(0, ChronoUnit.DAYS.between(loan.getDueAt(), returnedAt));
            ratePerDay = fine.getRatePerDay();
        } else {
            BookIncident incident = fine.getIncident();
            replacementCost = incident.getCopy().getPurchasePrice();
            incidentDescription = incident.getDescription();
        }

        List<PaymentRow> payments = fine.getPayments().stream()
                .filter(p -> p.getPaymentStatus() == PaymentStatus.Completed)
                .map(p -> new PaymentRow(p.getReceiptNumber(), p.getAmountPaid(), p.getPaymentMethod().name(), p.getPaidAt()))
                .toList();

        // "Any appeal" — the most recent one. A rejected appeal reinstates
        // the fine to Pending (business-rules §2), so a member can appeal
        // again later; this page shows whichever attempt is current.
        AppealView appealView = fine.getAppeals().stream()
                .max(Comparator.comparing(FineAppeal::getSubmittedAt))
                .map(a -> new AppealView(a.getAppealId(), a.getAppealReason(), a.getStatus().name(), a.getSubmittedAt(),
                        a.getDecidedAt(), a.getDecisionComments(), a.getApprovedReduction()))
                .orElse(null);

        boolean appealable = fine.getStatus() == FineStatus.Pending;

        return new MyFineDetailView(fine.getFineId(), fine.getFineType().name(), bookTitleOf(fine),
                fine.getAmountAssessed(), completedPaymentsTotal(fine), balanceOf(fine), fine.getStatus().dbValue(),
                fine.getAssessedAt(), daysOverdue, ratePerDay, replacementCost, incidentDescription, payments,
                appealView, appealable);
    }

    /** UC-06: "the member can submit an appeal on a Pending fine." Sets Fine.Status to Under Appeal — see this class's own javadoc on writing the status column directly. */
    @PreAuthorize("isAuthenticated()")
    public FineAppeal submitAppeal(Integer memberId, Integer fineId, AppealForm form) {
        Fine fine = ownedByMember(memberId, fineId);
        if (fine.getStatus() != FineStatus.Pending) {
            throw new FineException("You can only appeal a fine while it is Pending.");
        }
        FineAppeal appeal = new FineAppeal();
        appeal.setFine(fine);
        appeal.setAppealReason(form.getReason().trim());
        fine.getAppeals().add(appeal);
        fine.setStatus(FineStatus.UnderAppeal);
        fines.save(fine);
        return appeal;
    }
}
