package com.lms.borrowing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.borrowing.dto.CopyCandidateRow;
import com.lms.borrowing.dto.CurrentLoanRow;
import com.lms.borrowing.dto.LoanHistoryRow;
import com.lms.borrowing.dto.MemberEligibilityRow;
import com.lms.borrowing.dto.MyLoanRow;
import com.lms.borrowing.dto.MyLoansView;
import com.lms.borrowing.dto.ReturnResult;
import com.lms.common.domain.AppUser;
import com.lms.common.domain.AppUserRepository;
import com.lms.common.domain.Book;
import com.lms.common.domain.BookCopy;
import com.lms.common.domain.BookCopyRepository;
import com.lms.common.domain.BookCopyStatus;
import com.lms.common.domain.BookIncident;
import com.lms.common.domain.BookIncidentRepository;
import com.lms.common.domain.BookRepository;
import com.lms.common.domain.Fine;
import com.lms.common.domain.FineRepository;
import com.lms.common.domain.FineType;
import com.lms.common.domain.IncidentType;
import com.lms.common.domain.Loan;
import com.lms.common.domain.LoanRenewal;
import com.lms.common.domain.LoanRenewalRepository;
import com.lms.common.domain.LoanRepository;
import com.lms.common.domain.LoanStatus;
import com.lms.common.domain.Member;
import com.lms.common.domain.MemberRepository;
import com.lms.common.domain.MemberType;
import com.lms.common.domain.MembershipStatus;
import com.lms.common.domain.RenewalStatus;
import com.lms.common.domain.ReservationRepository;
import com.lms.common.domain.ReservationStatus;
import com.lms.common.domain.ReturnCondition;
import com.lms.common.domain.StaffProfile;
import com.lms.common.domain.StaffProfileRepository;
import com.lms.common.domain.SystemSettingRepository;
import com.lms.common.event.LoanReturnedEvent;
import com.lms.common.security.AuditAction;
import com.lms.common.web.SelectOption;

/**
 * UC-03 — issue, return and Loan History (not renewals, still out of this
 * task's scope). {@link #issue} and everything it depends on is untouched
 * from the earlier issue-only pass; {@link #returnBook} and {@link
 * #loanHistory} are new. Every business rule below comes straight from
 * business-rules.md §1/§2; nothing here is a made-up limit.
 *
 * <p>Search methods ({@link #searchMembers}, {@link #searchCopies}, {@link
 * #currentLoans}, {@link #loanHistory}) return typed DTOs, never entities —
 * every lazy association a row needs (member name, book title) is read
 * here, inside this class's own transaction, so the controller and
 * templates never risk the {@code LazyInitializationException} a detached
 * entity would throw once Hibernate's session has closed.
 */
@Service
@Transactional
public class BorrowingService {

    /** Rows per page for the Loan History screen. */
    static final int HISTORY_PAGE_SIZE = 20;

    private static final List<ReservationStatus> RESERVATION_BLOCK_STATUSES =
            List.of(ReservationStatus.Waiting, ReservationStatus.Ready);

    private final MemberRepository members;
    private final StaffProfileRepository staffProfiles;
    private final AppUserRepository appUsers;
    private final BookRepository books;
    private final BookCopyRepository copies;
    private final BookIncidentRepository incidents;
    private final LoanRepository loans;
    private final LoanRenewalRepository loanRenewals;
    private final FineRepository fines;
    private final ReservationRepository reservations;
    private final SystemSettingRepository settings;
    private final ApplicationEventPublisher events;

    public BorrowingService(MemberRepository members, StaffProfileRepository staffProfiles, AppUserRepository appUsers,
            BookRepository books, BookCopyRepository copies, BookIncidentRepository incidents, LoanRepository loans,
            LoanRenewalRepository loanRenewals, FineRepository fines, ReservationRepository reservations,
            SystemSettingRepository settings, ApplicationEventPublisher events) {
        this.members = members;
        this.staffProfiles = staffProfiles;
        this.appUsers = appUsers;
        this.books = books;
        this.copies = copies;
        this.incidents = incidents;
        this.loans = loans;
        this.loanRenewals = loanRenewals;
        this.fines = fines;
        this.reservations = reservations;
        this.settings = settings;
        this.events = events;
    }

    // ---- UC-03: "The Librarian searches for and selects the Library Member." ----

    @Transactional(readOnly = true)
    public List<MemberEligibilityRow> searchMembers(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return members.search(query.trim(), PageRequest.of(0, 10)).stream()
                .map(this::toEligibilityRow)
                .toList();
    }

    @Transactional(readOnly = true)
    public MemberEligibilityRow eligibility(Integer memberId) {
        return members.findById(memberId).map(this::toEligibilityRow).orElse(null);
    }

    private MemberEligibilityRow toEligibilityRow(Member member) {
        long activeCount = loans.countByMemberMemberIdAndStatus(member.getMemberId(), LoanStatus.Active);
        int limit = borrowingLimitFor(member.getMemberType());
        boolean hasOverdue = loans.existsByMemberMemberIdAndStatusAndDueAtBefore(
                member.getMemberId(), LoanStatus.Active, LocalDateTime.now());
        // R26: "Expired" is derived from ExpiryDate, never stored — a row can
        // still say MembershipStatus = 'Active' after its expiry date has
        // passed (nothing flips it automatically). Same derivation
        // account.html and StatusPillMapper's "membership" domain already
        // use, so the pill here never says "Active" for a membership that
        // has, in fact, expired.
        boolean expired = member.getExpiryDate().isBefore(LocalDate.now());
        boolean membershipActive = member.getMembershipStatus() == MembershipStatus.Active && !expired;
        boolean underLimit = activeCount < limit;
        String pillStatus = (member.getMembershipStatus() == MembershipStatus.Active && expired)
                ? "Expired" : member.getMembershipStatus().name();

        String reason = null;
        if (member.getMembershipStatus() != MembershipStatus.Active) {
            reason = "Membership is " + member.getMembershipStatus().name().toLowerCase() + ", not active.";
        } else if (expired) {
            reason = "Membership expired on " + member.getExpiryDate() + ".";
        } else if (hasOverdue) {
            reason = "This member has an overdue loan.";
        } else if (!underLimit) {
            reason = "Borrowing limit reached (" + activeCount + " of " + limit + ").";
        }

        // AppUser is loaded eagerly for Member (fetch = LAZY on the @OneToOne,
        // but read here, inside this transaction, same reasoning as the class
        // javadoc) — first/last name live there, not on Member itself.
        String fullName = member.getUser().getFirstName() + " " + member.getUser().getLastName();

        return new MemberEligibilityRow(member.getMemberId(), fullName, member.getMembershipNo(),
                member.getMemberType().dbValue(), pillStatus,
                activeCount, limit, membershipActive && !hasOverdue && underLimit, reason);
    }

    // ---- UC-03: "The Librarian searches for and selects an available book." ----

    /** Exact barcode match first (a desk scan); otherwise every copy of every book whose title matches. */
    @Transactional(readOnly = true)
    public List<CopyCandidateRow> searchCopies(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String trimmed = query.trim();

        Optional<BookCopy> byBarcode = copies.findByBarcode(trimmed);
        if (byBarcode.isPresent()) {
            return List.of(toCandidateRow(byBarcode.get()));
        }

        List<CopyCandidateRow> rows = new ArrayList<>();
        for (Book book : books.findByActiveTrueAndTitleContainingIgnoreCase(trimmed, PageRequest.of(0, 8))) {
            for (BookCopy copy : copies.findByBookBookIdOrderByAccessionNumber(book.getBookId())) {
                rows.add(toCandidateRow(copy, book.getTitle()));
            }
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public CopyCandidateRow copyCandidate(Integer copyId) {
        return copies.findById(copyId).map(this::toCandidateRow).orElse(null);
    }

    private CopyCandidateRow toCandidateRow(BookCopy copy) {
        return toCandidateRow(copy, copy.getBook().getTitle());
    }

    private CopyCandidateRow toCandidateRow(BookCopy copy, String bookTitle) {
        boolean eligible = copy.getStatus() == BookCopyStatus.Available && !copy.isReferenceOnly();
        return new CopyCandidateRow(copy.getCopyId(), copy.getAccessionNumber(), copy.getBarcode(), bookTitle,
                copy.getShelfLocation(), copy.getStatus().dbValue(), copy.isReferenceOnly(), eligible);
    }

    // ---- UC-03: issue the book ----

    /**
     * business-rules.md §1, checked in the order this task lists them:
     * membership active, borrowing limit not exceeded, no overdue loans,
     * copy not reference-only, copy available. Any failure throws {@link
     * BorrowingRuleException} — UC-03's own alternative flows ("the system
     * does not allow issuing it" / "the system prevents borrowing") — before
     * anything is written.
     */
    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    public Loan issue(Integer memberId, Integer copyId, Integer issuingUserId) {
        Member member = members.findById(memberId)
                .orElseThrow(() -> new NoSuchElementException("Member not found"));
        BookCopy copy = copies.findById(copyId)
                .orElseThrow(() -> new NoSuchElementException("Copy not found"));
        StaffProfile issuedBy = staffProfiles.findByUserUserId(issuingUserId)
                .orElseThrow(() -> new IllegalStateException("Signed-in user has no staff profile"));

        if (member.getMembershipStatus() != MembershipStatus.Active) {
            throw new BorrowingRuleException(
                    member.getUser().getFirstName() + " " + member.getUser().getLastName()
                            + "'s membership is " + member.getMembershipStatus().name().toLowerCase()
                            + ", not active.");
        }
        if (member.getExpiryDate().isBefore(LocalDate.now())) {
            throw new BorrowingRuleException(
                    member.getUser().getFirstName() + " " + member.getUser().getLastName()
                            + "'s membership expired on " + member.getExpiryDate() + ".");
        }

        long activeCount = loans.countByMemberMemberIdAndStatus(memberId, LoanStatus.Active);
        int limit = borrowingLimitFor(member.getMemberType());
        if (activeCount >= limit) {
            throw new BorrowingRuleException(
                    "Borrowing limit reached: " + activeCount + " of " + limit + " active loans.");
        }

        if (loans.existsByMemberMemberIdAndStatusAndDueAtBefore(memberId, LoanStatus.Active, LocalDateTime.now())) {
            throw new BorrowingRuleException(
                    "This member has an overdue loan. It must be returned before another book can be issued.");
        }

        if (copy.isReferenceOnly()) {
            throw new BorrowingRuleException(
                    "Copy " + copy.getAccessionNumber() + " is reference-only and does not circulate.");
        }
        if (copy.getStatus() != BookCopyStatus.Available) {
            throw new BorrowingRuleException(
                    "Copy " + copy.getAccessionNumber() + " is " + copy.getStatus().dbValue().toLowerCase()
                            + ", not available.");
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        Loan loan = new Loan();
        loan.setMember(member);
        loan.setCopy(copy);
        loan.setIssuedBy(issuedBy);
        loan.setBorrowedAt(now);
        loan.setDueAt(now.plusDays(loanPeriodDays()));
        loans.save(loan);

        copy.setStatus(BookCopyStatus.OnLoan);
        copies.save(copy);

        return loan;
    }

    private int borrowingLimitFor(MemberType memberType) {
        return settings.findById("Borrowing.Limit." + memberType.dbValue())
                .map(s -> Integer.parseInt(s.getSettingValue()))
                .orElse(memberType == MemberType.AcademicStaff ? 10 : 5);
    }

    private int loanPeriodDays() {
        return settings.findById("Loan.PeriodDays")
                .map(s -> Integer.parseInt(s.getSettingValue()))
                .orElse(14);
    }

    // ---- Current Loans screen ----

    @Transactional(readOnly = true)
    public List<CurrentLoanRow> currentLoans() {
        LocalDateTime now = LocalDateTime.now();
        return loans.findByStatusOrderByDueAtAsc(LoanStatus.Active).stream()
                .map(loan -> toRow(loan, now))
                .toList();
    }

    private CurrentLoanRow toRow(Loan loan, LocalDateTime now) {
        String memberName = loan.getMember().getUser().getFirstName() + " " + loan.getMember().getUser().getLastName();
        String bookTitle = loan.getCopy().getBook().getTitle();
        String pillStatus = now.isAfter(loan.getDueAt()) ? "Overdue" : loan.getStatus().name();
        return new CurrentLoanRow(loan.getLoanId(), memberName, bookTitle, loan.getBorrowedAt(), loan.getDueAt(),
                pillStatus, canRenew(loan, now));
    }

    // ---- Return the book ----

    /**
     * The return modal's condition select. {@link ReturnCondition} is the
     * whole legal set — see {@link #returnBook}'s own javadoc note on why
     * "New" is not among them.
     */
    public static List<SelectOption> returnConditionOptions() {
        return List.of(
                new SelectOption("Good", "Good"),
                new SelectOption("Fair", "Fair"),
                new SelectOption("Poor", "Poor"),
                new SelectOption("Damaged", "Damaged"));
    }

    /**
     * Every step in one transaction (this class's own default — see the
     * class-level {@code @Transactional} — so a failure partway through
     * leaves nothing half-applied): record the return, free the copy, and
     * — only when the loan was actually overdue — raise the Overdue fine.
     *
     * <p><b>Return condition options.</b> The task asked for "New, Good,
     * Fair, Poor, Damaged," but {@code CK_Loan_ReturnCondition} only
     * allows {@code Good, Fair, Poor, Damaged} — the same four values
     * {@link ReturnCondition} declares. "New" is not a legal value for
     * this column, and adding one is a schema change this task does not
     * make ("do not touch the database schema"). The form offers only the
     * four the schema actually accepts. Flagged here, not silently
     * dropped.
     */
    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    @AuditAction(action = "RETURN", entity = "Loan")
    public ReturnResult returnBook(Integer loanId, String returnConditionValue, Integer staffUserId) {
        Loan loan = loans.findById(loanId).orElseThrow(() -> new NoSuchElementException("Loan not found"));
        if (loan.getStatus() != LoanStatus.Active) {
            throw new BorrowingRuleException("This loan has already been returned.");
        }
        StaffProfile returnedTo = staffProfiles.findByUserUserId(staffUserId)
                .orElseThrow(() -> new IllegalStateException("Signed-in user has no staff profile"));

        ReturnCondition condition;
        try {
            condition = ReturnCondition.valueOf(returnConditionValue);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BorrowingRuleException("Unrecognised return condition.");
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        loan.setReturnedAt(now);
        loan.setReturnedTo(returnedTo);
        loan.setReturnCondition(condition);
        loan.setStatus(LoanStatus.Returned);
        loans.save(loan);

        BookCopy copy = loan.getCopy();
        boolean damaged = condition == ReturnCondition.Damaged;
        if (damaged) {
            copy.setStatus(BookCopyStatus.Damaged);
            BookIncident incident = new BookIncident();
            incident.setCopy(copy);
            incident.setLoan(loan);
            incident.setMember(loan.getMember());
            incident.setRecordedBy(returnedTo);
            incident.setIncidentType(IncidentType.Damaged);
            incident.setDescription("Reported damaged at return.");
            incidents.save(incident);
        } else {
            copy.setStatus(BookCopyStatus.Available);
        }
        copies.save(copy);

        events.publishEvent(new LoanReturnedEvent(loan.getLoanId(), loan.getMember().getMemberId(),
                copy.getBook().getBookId(), copy.getCopyId(), loan.getDueAt(), now, damaged));

        BigDecimal fineAmount = fines.findByLoanLoanIdAndFineType(loan.getLoanId(), FineType.Overdue)
                .map(Fine::getAmountAssessed)
                .orElse(null);

        String bookTitle = copy.getBook().getTitle();
        return new ReturnResult(loan.getLoanId(), bookTitle, fineAmount != null, fineAmount, damaged);
    }

    // ---- Loan History screen ----

    /** Every Returned loan, newest return first, optionally narrowed to one member or book title. */
    @Transactional(readOnly = true)
    public Page<LoanHistoryRow> loanHistory(String q, int page) {
        String likeQuery = (q == null || q.isBlank()) ? null : "%" + q.trim().toLowerCase() + "%";
        PageRequest pageable = PageRequest.of(Math.max(page - 1, 0), HISTORY_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "returnedAt"));
        return loans.search(LoanStatus.Returned, likeQuery, pageable).map(this::toHistoryRow);
    }

    private LoanHistoryRow toHistoryRow(Loan loan) {
        String memberName = loan.getMember().getUser().getFirstName() + " " + loan.getMember().getUser().getLastName();
        String bookTitle = loan.getCopy().getBook().getTitle();
        long daysOverdue = Math.max(0, ChronoUnit.DAYS.between(loan.getDueAt(), loan.getReturnedAt()));
        BigDecimal fineAmount = fines.findByLoanLoanIdAndFineType(loan.getLoanId(), FineType.Overdue)
                .map(Fine::getAmountAssessed)
                .orElse(null);
        return new LoanHistoryRow(loan.getLoanId(), memberName, bookTitle, loan.getBorrowedAt(), loan.getReturnedAt(),
                daysOverdue, fineAmount);
    }

    private int renewalMaxPerLoan() {
        return settings.findById("Renewal.MaxPerLoan")
                .map(s -> Integer.parseInt(s.getSettingValue()))
                .orElse(2);
    }

    private int renewalExtensionDays() {
        return settings.findById("Renewal.ExtensionDays")
                .map(s -> Integer.parseInt(s.getSettingValue()))
                .orElse(14);
    }

    @PreAuthorize("hasAuthority('Librarian') or hasAuthority('Library Administrator')")
    @AuditAction(action = "RENEW", entity = "LoanRenewal")
    public LoanRenewal renewByStaff(Integer loanId, Integer staffUserId) {
        Loan loan = loans.findById(loanId).orElseThrow(() -> new NoSuchElementException("Loan not found"));
        StaffProfile staff = staffProfiles.findByUserUserId(staffUserId)
                .orElseThrow(() -> new IllegalStateException("Signed-in user has no staff profile"));
        return applyRenewal(loan, staffUserId, staff);
    }

    @PreAuthorize("isAuthenticated()")
    @AuditAction(action = "RENEW", entity = "LoanRenewal")
    public LoanRenewal requestRenewal(Integer memberId, Integer loanId, Integer memberUserId) {
        Loan loan = loans.findById(loanId)
                .filter(l -> l.getMember().getMemberId().equals(memberId))
                .orElseThrow(() -> new NoSuchElementException("Loan not found"));
        return applyRenewal(loan, memberUserId, null);
    }

    private LoanRenewal applyRenewal(Loan loan, Integer actingUserId, StaffProfile approvedBy) {
        if (loan.getStatus() != LoanStatus.Active) {
            throw new BorrowingRuleException("This loan is not active.");
        }
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        if (loan.getDueAt().isBefore(now)) {
            throw new BorrowingRuleException("This loan is overdue and cannot be renewed.");
        }
        long approvedCount = loan.getRenewals().stream().filter(r -> r.getStatus() == RenewalStatus.Approved).count();
        if (approvedCount >= renewalMaxPerLoan()) {
            throw new BorrowingRuleException("This loan has already reached the maximum number of renewals.");
        }
        Integer bookId = loan.getCopy().getBook().getBookId();
        if (reservations.existsByBookBookIdAndMemberMemberIdNotAndStatusIn(
                bookId, loan.getMember().getMemberId(), RESERVATION_BLOCK_STATUSES)) {
            throw new BorrowingRuleException("Another member is waiting for this title, so it cannot be renewed.");
        }

        AppUser requestedBy = appUsers.getReferenceById(actingUserId);
        LocalDateTime oldDueAt = loan.getDueAt();
        LocalDateTime newDueAt = oldDueAt.plusDays(renewalExtensionDays());

        LoanRenewal renewal = new LoanRenewal();
        renewal.setLoan(loan);
        renewal.setRequestedBy(requestedBy);
        renewal.setOldDueAt(oldDueAt);
        renewal.setNewDueAt(newDueAt);
        renewal.setStatus(RenewalStatus.Approved);
        renewal.setApprovedBy(approvedBy);
        loanRenewals.save(renewal);
        loan.getRenewals().add(renewal);
        loan.setDueAt(newDueAt);
        loans.save(loan);
        return renewal;
    }

    private boolean canRenew(Loan loan, LocalDateTime now) {
        if (loan.getStatus() != LoanStatus.Active || loan.getDueAt().isBefore(now)) {
            return false;
        }
        long approvedCount = loan.getRenewals().stream().filter(r -> r.getStatus() == RenewalStatus.Approved).count();
        if (approvedCount >= renewalMaxPerLoan()) {
            return false;
        }
        Integer bookId = loan.getCopy().getBook().getBookId();
        return !reservations.existsByBookBookIdAndMemberMemberIdNotAndStatusIn(
                bookId, loan.getMember().getMemberId(), RESERVATION_BLOCK_STATUSES);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public MyLoansView myLoans(Integer memberId) {
        LocalDateTime now = LocalDateTime.now();
        List<Loan> all = loans.findByMemberMemberIdOrderByBorrowedAtDesc(memberId, PageRequest.of(0, 200)).getContent();
        List<MyLoanRow> current = all.stream()
                .filter(l -> l.getStatus() == LoanStatus.Active)
                .map(l -> toMyLoanRow(l, now))
                .toList();
        List<MyLoanRow> past = all.stream()
                .filter(l -> l.getStatus() != LoanStatus.Active)
                .map(l -> toMyLoanRow(l, now))
                .toList();
        return new MyLoansView(current, past);
    }

    private MyLoanRow toMyLoanRow(Loan loan, LocalDateTime now) {
        String bookTitle = loan.getCopy().getBook().getTitle();
        String pillStatus = loan.getStatus() == LoanStatus.Active && now.isAfter(loan.getDueAt())
                ? "Overdue" : loan.getStatus().name();
        return new MyLoanRow(loan.getLoanId(), bookTitle, loan.getBorrowedAt(), loan.getDueAt(),
                loan.getReturnedAt(), pillStatus, canRenew(loan, now));
    }
}
