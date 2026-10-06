package com.lms.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.lms.common.domain.AppUserRepository;
import com.lms.common.domain.BookCopyRepository;
import com.lms.common.domain.BookReviewRepository;
import com.lms.common.domain.CategoryRepository;
import com.lms.common.domain.FeedbackCategoryRepository;
import com.lms.common.domain.FineRepository;
import com.lms.common.domain.LoanRepository;
import com.lms.common.domain.MemberFeedbackRepository;
import com.lms.common.domain.MemberRepository;
import com.lms.common.domain.ReportAudit;
import com.lms.common.domain.ReportAuditRepository;
import com.lms.common.domain.ReportType;
import com.lms.common.domain.ReservationRepository;
import com.lms.common.domain.SystemSettingRepository;
import com.lms.common.web.SelectOption;
import com.lms.report.dto.BorrowingReportRow;
import com.lms.report.dto.FeedbackReportRow;
import com.lms.report.dto.FinesReportRow;
import com.lms.report.dto.InventoryStatusRow;
import com.lms.report.dto.InventoryTitleRow;
import com.lms.report.dto.OverdueReportRow;
import com.lms.report.dto.ReportHistoryRow;
import com.lms.report.dto.ReservationsReportRow;
import com.lms.report.dto.ReviewsReportRow;
import com.lms.report.dto.UsersReportRow;

@Service
@Transactional(readOnly = true)
public class ReportService {

    static final int HISTORY_PAGE_SIZE = 20;

    private static final String STAFF_ROLE = "hasAuthority('Librarian') or hasAuthority('Library Administrator')";
    private static final String ADMIN_ROLE = "hasAuthority('Library Administrator')";

    private final LoanRepository loans;
    private final FineRepository fines;
    private final ReservationRepository reservations;
    private final MemberRepository members;
    private final BookCopyRepository bookCopies;
    private final BookReviewRepository bookReviews;
    private final MemberFeedbackRepository memberFeedback;
    private final ReportAuditRepository reportAudits;
    private final AppUserRepository appUsers;
    private final SystemSettingRepository settings;
    private final CategoryRepository categories;
    private final FeedbackCategoryRepository feedbackCategories;
    private final ObjectMapper objectMapper;

    public ReportService(LoanRepository loans, FineRepository fines, ReservationRepository reservations,
            MemberRepository members, BookCopyRepository bookCopies, BookReviewRepository bookReviews,
            MemberFeedbackRepository memberFeedback, ReportAuditRepository reportAudits, AppUserRepository appUsers,
            SystemSettingRepository settings, CategoryRepository categories,
            FeedbackCategoryRepository feedbackCategories, ObjectMapper objectMapper) {
        this.loans = loans;
        this.fines = fines;
        this.reservations = reservations;
        this.members = members;
        this.bookCopies = bookCopies;
        this.bookReviews = bookReviews;
        this.memberFeedback = memberFeedback;
        this.reportAudits = reportAudits;
        this.appUsers = appUsers;
        this.settings = settings;
        this.categories = categories;
        this.feedbackCategories = feedbackCategories;
        this.objectMapper = objectMapper;
    }

    public List<SelectOption> categoryOptions() {
        List<SelectOption> options = new ArrayList<>();
        options.add(new SelectOption("", "All categories"));
        categories.findByActiveTrueOrderByCategoryName()
                .forEach(c -> options.add(new SelectOption(c.getCategoryId().toString(), c.getCategoryName())));
        return options;
    }

    public List<SelectOption> feedbackCategoryOptions() {
        List<SelectOption> options = new ArrayList<>();
        options.add(new SelectOption("", "All categories"));
        feedbackCategories.findAllByOrderByCategoryName()
                .forEach(c -> options.add(new SelectOption(c.getFeedbackCategoryId().toString(), c.getCategoryName())));
        return options;
    }

    public static List<SelectOption> memberTypeOptions() {
        return List.of(
                new SelectOption("", "All member types"),
                new SelectOption("Student", "Student"),
                new SelectOption("Academic Staff", "Academic Staff"));
    }

    public static List<SelectOption> fineTypeOptions() {
        return List.of(
                new SelectOption("", "All fine types"),
                new SelectOption("Overdue", "Overdue"),
                new SelectOption("Lost", "Lost"),
                new SelectOption("Damaged", "Damaged"));
    }

    public static List<SelectOption> reportTypeOptions() {
        List<SelectOption> options = new ArrayList<>();
        options.add(new SelectOption("", "All report types"));
        for (ReportType type : List.of(ReportType.BorrowedBooks, ReportType.OverdueBooks, ReportType.FineCollection,
                ReportType.Reservations, ReportType.MemberRegistration, ReportType.BookInventory, ReportType.Reviews,
                ReportType.Feedback)) {
            options.add(new SelectOption(type.name(), type.name()));
        }
        return options;
    }

    private static void requireValidRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ReportException("Choose both a start and an end date.");
        }
        if (to.isBefore(from)) {
            throw new ReportException("The end date cannot be before the start date.");
        }
    }

    private static LocalDateTime startOf(LocalDate date) {
        return date.atStartOfDay();
    }

    private static LocalDateTime endOf(LocalDate date) {
        return date.atTime(LocalTime.MAX);
    }

    // ================= Borrowing =================

    @PreAuthorize(STAFF_ROLE)
    public BorrowingReport borrowingReport(LocalDate from, LocalDate to, String memberType, Integer categoryId,
            Integer requestedByUserId) {
        requireValidRange(from, to);
        LocalDateTime start = startOf(from);
        LocalDateTime end = endOf(to);

        List<BorrowingReportRow> rows = new ArrayList<>();
        for (Object[] r : loans.borrowingByMemberTypeAndCategory(start, end, memberType, categoryId)) {
            rows.add(new BorrowingReportRow(asString(r[0]), asString(r[1]), asLong(r[2])));
        }
        Object[] summary = loans.borrowingSummary(start, end, memberType, categoryId).get(0);

        recordAudit(ReportType.BorrowedBooks, requestedByUserId,
                filters("from", from, "to", to, "memberType", memberType, "categoryId", categoryId));

        return new BorrowingReport(rows, asLong(summary[0]), asLong(summary[1]), asLong(summary[2]));
    }

    public record BorrowingReport(List<BorrowingReportRow> rows, long totalLoans, long uniqueMembers,
                                   long uniqueTitles) {
    }

    // ================= Overdue =================

    @PreAuthorize(STAFF_ROLE)
    public OverdueReport overdueReport(LocalDate from, LocalDate to, String memberType, Integer requestedByUserId) {
        requireValidRange(from, to);
        LocalDateTime start = startOf(from);
        LocalDateTime end = endOf(to);
        BigDecimal rate = fineRatePerDay();
        BigDecimal cap = fineMaxPerLoan();

        List<OverdueReportRow> rows = new ArrayList<>();
        for (Object[] r : loans.overdueRows(start, end, memberType)) {
            long daysOverdue = asLong(r[3]);
            BigDecimal fineAccrued = rate.multiply(BigDecimal.valueOf(daysOverdue)).min(cap);
            rows.add(new OverdueReportRow(asString(r[0]), asString(r[1]), (LocalDateTime) r[2], daysOverdue, fineAccrued));
        }
        Object[] summary = loans.overdueSummary(start, end, memberType, rate, cap).get(0);

        recordAudit(ReportType.OverdueBooks, requestedByUserId, filters("from", from, "to", to, "memberType", memberType));

        return new OverdueReport(rows, asLong(summary[0]), asDouble(summary[1]), asMoney(summary[2]));
    }

    public record OverdueReport(List<OverdueReportRow> rows, long overdueCount, Double averageDaysOverdue,
                                 BigDecimal totalFineAccrued) {
    }

    // ================= Fines =================

    @PreAuthorize(STAFF_ROLE)
    public FinesReport finesReport(LocalDate from, LocalDate to, String fineType, Integer requestedByUserId) {
        requireValidRange(from, to);
        LocalDateTime start = startOf(from);
        LocalDateTime end = endOf(to);

        List<FinesReportRow> rows = new ArrayList<>();
        BigDecimal totalAssessed = BigDecimal.ZERO;
        BigDecimal totalCollected = BigDecimal.ZERO;
        BigDecimal totalWaived = BigDecimal.ZERO;
        BigDecimal totalOutstanding = BigDecimal.ZERO;
        for (Object[] r : fines.finesByType(start, end, fineType)) {
            BigDecimal assessed = asMoney(r[1]);
            BigDecimal collected = asMoney(r[2]);
            BigDecimal waived = asMoney(r[3]);
            BigDecimal outstanding = asMoney(r[4]);
            rows.add(new FinesReportRow(asString(r[0]), assessed, collected, waived, outstanding));
            totalAssessed = totalAssessed.add(assessed);
            totalCollected = totalCollected.add(collected);
            totalWaived = totalWaived.add(waived);
            totalOutstanding = totalOutstanding.add(outstanding);
        }

        recordAudit(ReportType.FineCollection, requestedByUserId, filters("from", from, "to", to, "fineType", fineType));

        return new FinesReport(rows, totalAssessed, totalCollected, totalWaived, totalOutstanding);
    }

    public record FinesReport(List<FinesReportRow> rows, BigDecimal totalAssessed, BigDecimal totalCollected,
                               BigDecimal totalWaived, BigDecimal totalOutstanding) {
    }

    // ================= Reservations =================

    @PreAuthorize(STAFF_ROLE)
    public ReservationsReport reservationsReport(LocalDate from, LocalDate to, Integer requestedByUserId) {
        requireValidRange(from, to);
        LocalDateTime start = startOf(from);
        LocalDateTime end = endOf(to);

        List<ReservationsReportRow> rows = new ArrayList<>();
        for (Object[] r : reservations.reservationsByTitle(start, end)) {
            rows.add(new ReservationsReportRow(asString(r[0]), asLong(r[1]), asLong(r[2]), asLong(r[3]), asLong(r[4])));
        }
        Object[] summary = reservations.reservationsSummary(start, end).get(0);
        long total = asLong(summary[0]);
        long fulfilled = asLong(summary[1]);
        long expired = asLong(summary[2]);
        Double fulfilmentRate = total == 0 ? null : (fulfilled * 100.0) / total;
        Double expiryRate = total == 0 ? null : (expired * 100.0) / total;

        recordAudit(ReportType.Reservations, requestedByUserId, filters("from", from, "to", to));

        return new ReservationsReport(rows, total, fulfilmentRate, expiryRate);
    }

    public record ReservationsReport(List<ReservationsReportRow> rows, long totalReservations,
                                      Double fulfilmentRatePct, Double expiryRatePct) {
    }

    // ================= Users =================

    @PreAuthorize(STAFF_ROLE)
    public UsersReport usersReport(LocalDate from, LocalDate to, String memberType, Integer requestedByUserId) {
        requireValidRange(from, to);

        List<UsersReportRow> rows = new ArrayList<>();
        long totalRegistrations = 0;
        long totalActive = 0;
        long totalSuspended = 0;
        for (Object[] r : members.usersByMemberType(from, to, memberType)) {
            long registrations = asLong(r[1]);
            long active = asLong(r[2]);
            long suspended = asLong(r[3]);
            rows.add(new UsersReportRow(asString(r[0]), registrations, active, suspended));
            totalRegistrations += registrations;
            totalActive += active;
            totalSuspended += suspended;
        }

        recordAudit(ReportType.MemberRegistration, requestedByUserId, filters("from", from, "to", to, "memberType", memberType));

        return new UsersReport(rows, totalRegistrations, totalActive, totalSuspended);
    }

    public record UsersReport(List<UsersReportRow> rows, long totalRegistrations, long currentlyActive,
                               long currentlySuspended) {
    }

    // ================= Inventory =================

    @PreAuthorize(STAFF_ROLE)
    public InventoryReport inventoryReport(LocalDate from, LocalDate to, Integer categoryId, Integer requestedByUserId) {
        requireValidRange(from, to);
        LocalDateTime start = startOf(from);
        LocalDateTime end = endOf(to);

        List<InventoryStatusRow> statusRows = new ArrayList<>();
        for (Object[] r : bookCopies.countGroupedByStatus()) {
            statusRows.add(new InventoryStatusRow(asString(r[0]), asLong(r[1])));
        }

        List<InventoryTitleRow> mostBorrowed = new ArrayList<>();
        for (Object[] r : loans.mostBorrowedTitles(start, end, categoryId)) {
            mostBorrowed.add(new InventoryTitleRow(asString(r[0]), asLong(r[1])));
        }

        List<InventoryTitleRow> nothingAvailable = new ArrayList<>();
        for (Object[] r : bookCopies.titlesWithNothingAvailable()) {
            nothingAvailable.add(new InventoryTitleRow(asString(r[0]), asLong(r[1])));
        }

        Object[] summary = bookCopies.inventorySummary().get(0);

        recordAudit(ReportType.BookInventory, requestedByUserId, filters("from", from, "to", to, "categoryId", categoryId));

        return new InventoryReport(statusRows, mostBorrowed, nothingAvailable, asLong(summary[0]), asLong(summary[1]),
                nothingAvailable.size());
    }

    public record InventoryReport(List<InventoryStatusRow> statusRows, List<InventoryTitleRow> mostBorrowedRows,
                                   List<InventoryTitleRow> nothingAvailableRows, long totalCopies, long availableNow,
                                   long titlesWithNothingAvailable) {
    }

    // ================= Reviews =================

    @PreAuthorize(STAFF_ROLE)
    public ReviewsReport reviewsReport(LocalDate from, LocalDate to, Integer requestedByUserId) {
        requireValidRange(from, to);
        LocalDateTime start = startOf(from);
        LocalDateTime end = endOf(to);

        List<ReviewsReportRow> rows = new ArrayList<>();
        for (Object[] r : bookReviews.reviewsByBook(start, end)) {
            rows.add(new ReviewsReportRow(asString(r[0]), asLong(r[1]), asLong(r[2]), asLong(r[3]), asDouble(r[4])));
        }
        Object[] summary = bookReviews.reviewsSummary(start, end).get(0);

        recordAudit(ReportType.Reviews, requestedByUserId, filters("from", from, "to", to));

        return new ReviewsReport(rows, asLong(summary[0]), asLong(summary[1]), asDouble(summary[3]));
    }

    public record ReviewsReport(List<ReviewsReportRow> rows, long totalSubmitted, long totalApproved,
                                 Double overallAverageRating) {
    }

    // ================= Feedback =================

    @PreAuthorize(STAFF_ROLE)
    public FeedbackReport feedbackReport(LocalDate from, LocalDate to, Integer categoryId, Integer requestedByUserId) {
        requireValidRange(from, to);
        LocalDateTime start = startOf(from);
        LocalDateTime end = endOf(to);

        List<FeedbackReportRow> rows = new ArrayList<>();
        for (Object[] r : memberFeedback.feedbackByCategoryAndStatus(start, end, categoryId)) {
            rows.add(new FeedbackReportRow(asString(r[0]), asString(r[1]), asLong(r[2])));
        }
        Object[] summary = memberFeedback.feedbackSummary(start, end, categoryId).get(0);

        recordAudit(ReportType.Feedback, requestedByUserId, filters("from", from, "to", to, "categoryId", categoryId));

        return new FeedbackReport(rows, asLong(summary[0]), asLong(summary[1]), asDouble(summary[2]));
    }

    public record FeedbackReport(List<FeedbackReportRow> rows, long totalSubmissions, long resolvedOrClosed,
                                  Double averageResolutionDays) {
    }

    // ================= Report History =================

    @PreAuthorize(ADMIN_ROLE)
    public Page<ReportHistoryRow> reportHistory(String q, String reportType, LocalDate from, LocalDate to, int page) {
        String likeQuery = (q == null || q.isBlank()) ? null : "%" + q.trim().toLowerCase() + "%";
        ReportType typeFilter = (reportType == null || reportType.isBlank()) ? null : ReportType.valueOf(reportType);
        LocalDateTime fromAt = from == null ? null : startOf(from);
        LocalDateTime toAt = to == null ? null : endOf(to);
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), HISTORY_PAGE_SIZE);
        return reportAudits.search(likeQuery, typeFilter, fromAt, toAt, pageable).map(this::toHistoryRow);
    }

    private ReportHistoryRow toHistoryRow(ReportAudit audit) {
        String name = audit.getRequestedBy().getFirstName() + " " + audit.getRequestedBy().getLastName();
        return new ReportHistoryRow(audit.getReportAuditId(), name, audit.getReportType().name(),
                audit.getFilterJson(), audit.getGeneratedAt());
    }

    // ================= Shared helpers =================

    private BigDecimal fineRatePerDay() {
        return settings.findById("Fine.RatePerDay").map(s -> new BigDecimal(s.getSettingValue())).orElse(new BigDecimal("20.00"));
    }

    private BigDecimal fineMaxPerLoan() {
        return settings.findById("Fine.MaxPerLoan").map(s -> new BigDecimal(s.getSettingValue())).orElse(new BigDecimal("500.00"));
    }

    private void recordAudit(ReportType type, Integer userId, Map<String, Object> filterValues) {
        ReportAudit audit = new ReportAudit();
        audit.setRequestedBy(appUsers.getReferenceById(userId));
        audit.setReportType(type);
        audit.setFilterJson(toJson(filterValues));
        reportAudits.save(audit);
    }

    private String toJson(Map<String, Object> filterValues) {
        try {
            return objectMapper.writeValueAsString(filterValues);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise report filters as JSON", e);
        }
    }

    private static Map<String, Object> filters(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private static long asLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    private static Double asDouble(Object o) {
        return o == null ? null : ((Number) o).doubleValue();
    }

    private static BigDecimal asMoney(Object o) {
        if (o == null) {
            return BigDecimal.ZERO;
        }
        if (o instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(o.toString());
    }
}
