package com.lms.report;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.lms.common.security.AppUserPrincipal;
import com.lms.common.web.DataTableColumn;
import com.lms.common.web.StatTile;
import com.lms.report.dto.ReportHistoryRow;
import com.lms.report.dto.ReportIndexEntry;

@Controller
public class ReportController {

    private static final String STAFF_ROLE = "hasAuthority('Librarian') or hasAuthority('Library Administrator')";
    private static final String ADMIN_ROLE = "hasAuthority('Library Administrator')";

    private static final List<ReportIndexEntry> REPORTS = List.of(
            new ReportIndexEntry("borrowing", "Borrowing", "Loans issued in the period, by member type and category.", "arrow-left-right"),
            new ReportIndexEntry("overdue", "Overdue", "Current overdue loans with days overdue and fine accrued.", "octagon-alert"),
            new ReportIndexEntry("fines", "Fines", "Assessed, collected, waived and outstanding totals.", "wallet"),
            new ReportIndexEntry("reservations", "Reservations", "Queue lengths, fulfilment and expiry rates by title.", "calendar-clock"),
            new ReportIndexEntry("users", "Users", "Registrations, active vs suspended, by member type.", "users"),
            new ReportIndexEntry("inventory", "Inventory", "Copies by status, most-borrowed titles, titles with nothing available.", "book-open"),
            new ReportIndexEntry("reviews", "Reviews", "Submitted, approved, rejected counts and average rating by book.", "star"),
            new ReportIndexEntry("feedback", "Feedback", "Submissions by category and status, and resolution time.", "message-square"));

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PreAuthorize(STAFF_ROLE)
    @GetMapping("/reports")
    public String index(Model model) {
        model.addAttribute("pageTitle", "Reports and Analytics");
        model.addAttribute("reports", REPORTS);
        return "report/index";
    }

    private static LocalDate defaultFrom(LocalDate from) {
        return from != null ? from : LocalDate.now().minusDays(30);
    }

    private static LocalDate defaultTo(LocalDate to) {
        return to != null ? to : LocalDate.now();
    }

    @PreAuthorize(STAFF_ROLE)
    @GetMapping("/reports/borrowing")
    public String borrowing(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String memberType, @RequestParam(required = false) Integer categoryId,
            @AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        LocalDate f = defaultFrom(from);
        LocalDate t = defaultTo(to);
        model.addAttribute("pageTitle", "Borrowing Report");
        model.addAttribute("from", f);
        model.addAttribute("to", t);
        model.addAttribute("memberType", memberType);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("memberTypeOptions", ReportService.memberTypeOptions());
        model.addAttribute("categoryOptions", reportService.categoryOptions());
        model.addAttribute("columns", List.of(
                DataTableColumn.left("Member type"), DataTableColumn.left("Category"), DataTableColumn.right("Loans")));
        try {
            ReportService.BorrowingReport report = reportService.borrowingReport(f, t, blank(memberType), categoryId, principal.userId());
            model.addAttribute("rows", report.rows());
            model.addAttribute("tiles", List.of(
                    new StatTile("Total Loans", String.valueOf(report.totalLoans())),
                    new StatTile("Unique Members", String.valueOf(report.uniqueMembers())),
                    new StatTile("Unique Titles", String.valueOf(report.uniqueTitles()))));
        } catch (ReportException e) {
            model.addAttribute("rows", List.of());
            model.addAttribute("tiles", List.of());
            model.addAttribute("flashErrorTitle", "Cannot run this report");
            model.addAttribute("flashErrorMessage", e.getMessage());
        }
        return "report/borrowing";
    }

    @PreAuthorize(STAFF_ROLE)
    @GetMapping("/reports/overdue")
    public String overdue(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String memberType,
            @AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        LocalDate f = defaultFrom(from);
        LocalDate t = defaultTo(to);
        model.addAttribute("pageTitle", "Overdue Report");
        model.addAttribute("from", f);
        model.addAttribute("to", t);
        model.addAttribute("memberType", memberType);
        model.addAttribute("memberTypeOptions", ReportService.memberTypeOptions());
        model.addAttribute("columns", List.of(
                DataTableColumn.left("Member"), DataTableColumn.left("Book"), DataTableColumn.right("Due date"),
                DataTableColumn.right("Days overdue"), DataTableColumn.right("Fine accrued")));
        try {
            ReportService.OverdueReport report = reportService.overdueReport(f, t, blank(memberType), principal.userId());
            model.addAttribute("rows", report.rows());
            model.addAttribute("tiles", List.of(
                    new StatTile("Overdue Loans", String.valueOf(report.overdueCount()), report.overdueCount() > 0),
                    new StatTile("Total Fine Accrued", "LKR " + report.totalFineAccrued()),
                    new StatTile("Average Days Overdue", report.averageDaysOverdue() == null ? "—" : String.format("%.1f", report.averageDaysOverdue()))));
        } catch (ReportException e) {
            model.addAttribute("rows", List.of());
            model.addAttribute("tiles", List.of());
            model.addAttribute("flashErrorTitle", "Cannot run this report");
            model.addAttribute("flashErrorMessage", e.getMessage());
        }
        return "report/overdue";
    }

    @PreAuthorize(STAFF_ROLE)
    @GetMapping("/reports/fines")
    public String fines(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String fineType,
            @AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        LocalDate f = defaultFrom(from);
        LocalDate t = defaultTo(to);
        model.addAttribute("pageTitle", "Fines Report");
        model.addAttribute("from", f);
        model.addAttribute("to", t);
        model.addAttribute("fineType", fineType);
        model.addAttribute("fineTypeOptions", ReportService.fineTypeOptions());
        model.addAttribute("columns", List.of(
                DataTableColumn.left("Fine type"), DataTableColumn.right("Assessed"), DataTableColumn.right("Collected"),
                DataTableColumn.right("Waived"), DataTableColumn.right("Outstanding")));
        try {
            ReportService.FinesReport report = reportService.finesReport(f, t, blank(fineType), principal.userId());
            model.addAttribute("rows", report.rows());
            model.addAttribute("tiles", List.of(
                    new StatTile("Assessed", "LKR " + report.totalAssessed()),
                    new StatTile("Collected", "LKR " + report.totalCollected()),
                    new StatTile("Waived", "LKR " + report.totalWaived()),
                    new StatTile("Outstanding", "LKR " + report.totalOutstanding(), report.totalOutstanding().signum() > 0)));
        } catch (ReportException e) {
            model.addAttribute("rows", List.of());
            model.addAttribute("tiles", List.of());
            model.addAttribute("flashErrorTitle", "Cannot run this report");
            model.addAttribute("flashErrorMessage", e.getMessage());
        }
        return "report/fines";
    }

    @PreAuthorize(STAFF_ROLE)
    @GetMapping("/reports/reservations")
    public String reservations(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        LocalDate f = defaultFrom(from);
        LocalDate t = defaultTo(to);
        model.addAttribute("pageTitle", "Reservations Report");
        model.addAttribute("from", f);
        model.addAttribute("to", t);
        model.addAttribute("columns", List.of(
                DataTableColumn.left("Title"), DataTableColumn.right("Requested"), DataTableColumn.right("Fulfilled"),
                DataTableColumn.right("Expired"), DataTableColumn.right("Current queue")));
        try {
            ReportService.ReservationsReport report = reportService.reservationsReport(f, t, principal.userId());
            model.addAttribute("rows", report.rows());
            model.addAttribute("tiles", List.of(
                    new StatTile("Total Reservations", String.valueOf(report.totalReservations())),
                    new StatTile("Fulfilment Rate", report.fulfilmentRatePct() == null ? "—" : String.format("%.0f%%", report.fulfilmentRatePct())),
                    new StatTile("Expiry Rate", report.expiryRatePct() == null ? "—" : String.format("%.0f%%", report.expiryRatePct()))));
        } catch (ReportException e) {
            model.addAttribute("rows", List.of());
            model.addAttribute("tiles", List.of());
            model.addAttribute("flashErrorTitle", "Cannot run this report");
            model.addAttribute("flashErrorMessage", e.getMessage());
        }
        return "report/reservations";
    }

    @PreAuthorize(STAFF_ROLE)
    @GetMapping("/reports/users")
    public String users(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String memberType,
            @AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        LocalDate f = defaultFrom(from);
        LocalDate t = defaultTo(to);
        model.addAttribute("pageTitle", "Users Report");
        model.addAttribute("from", f);
        model.addAttribute("to", t);
        model.addAttribute("memberType", memberType);
        model.addAttribute("memberTypeOptions", ReportService.memberTypeOptions());
        model.addAttribute("columns", List.of(
                DataTableColumn.left("Member type"), DataTableColumn.right("Registrations"),
                DataTableColumn.right("Active"), DataTableColumn.right("Suspended")));
        try {
            ReportService.UsersReport report = reportService.usersReport(f, t, blank(memberType), principal.userId());
            model.addAttribute("rows", report.rows());
            model.addAttribute("tiles", List.of(
                    new StatTile("Registrations", String.valueOf(report.totalRegistrations())),
                    new StatTile("Currently Active", String.valueOf(report.currentlyActive())),
                    new StatTile("Currently Suspended", String.valueOf(report.currentlySuspended()))));
        } catch (ReportException e) {
            model.addAttribute("rows", List.of());
            model.addAttribute("tiles", List.of());
            model.addAttribute("flashErrorTitle", "Cannot run this report");
            model.addAttribute("flashErrorMessage", e.getMessage());
        }
        return "report/users";
    }

    @PreAuthorize(STAFF_ROLE)
    @GetMapping("/reports/inventory")
    public String inventory(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Integer categoryId,
            @AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        LocalDate f = defaultFrom(from);
        LocalDate t = defaultTo(to);
        model.addAttribute("pageTitle", "Inventory Report");
        model.addAttribute("from", f);
        model.addAttribute("to", t);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("categoryOptions", reportService.categoryOptions());
        model.addAttribute("statusColumns", List.of(DataTableColumn.left("Status"), DataTableColumn.right("Copies")));
        model.addAttribute("titleColumns", List.of(DataTableColumn.left("Title"), DataTableColumn.right("Times borrowed")));
        model.addAttribute("nothingAvailableColumns", List.of(DataTableColumn.left("Title"), DataTableColumn.right("Total copies")));
        try {
            ReportService.InventoryReport report = reportService.inventoryReport(f, t, categoryId, principal.userId());
            model.addAttribute("statusRows", report.statusRows());
            model.addAttribute("titleRows", report.mostBorrowedRows());
            model.addAttribute("nothingAvailableRows", report.nothingAvailableRows());
            model.addAttribute("tiles", List.of(
                    new StatTile("Total Copies", String.valueOf(report.totalCopies())),
                    new StatTile("Available Now", String.valueOf(report.availableNow())),
                    new StatTile("Titles With Nothing Available", String.valueOf(report.titlesWithNothingAvailable()), report.titlesWithNothingAvailable() > 0)));
        } catch (ReportException e) {
            model.addAttribute("statusRows", List.of());
            model.addAttribute("titleRows", List.of());
            model.addAttribute("nothingAvailableRows", List.of());
            model.addAttribute("tiles", List.of());
            model.addAttribute("flashErrorTitle", "Cannot run this report");
            model.addAttribute("flashErrorMessage", e.getMessage());
        }
        return "report/inventory";
    }

    @PreAuthorize(STAFF_ROLE)
    @GetMapping("/reports/reviews")
    public String reviews(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        LocalDate f = defaultFrom(from);
        LocalDate t = defaultTo(to);
        model.addAttribute("pageTitle", "Reviews Report");
        model.addAttribute("from", f);
        model.addAttribute("to", t);
        model.addAttribute("columns", List.of(
                DataTableColumn.left("Book"), DataTableColumn.right("Submitted"), DataTableColumn.right("Approved"),
                DataTableColumn.right("Rejected"), DataTableColumn.right("Avg rating")));
        try {
            ReportService.ReviewsReport report = reportService.reviewsReport(f, t, principal.userId());
            model.addAttribute("rows", report.rows());
            model.addAttribute("tiles", List.of(
                    new StatTile("Submitted", String.valueOf(report.totalSubmitted())),
                    new StatTile("Approved", String.valueOf(report.totalApproved())),
                    new StatTile("Average Rating", report.overallAverageRating() == null ? "—" : String.format("%.1f", report.overallAverageRating()))));
        } catch (ReportException e) {
            model.addAttribute("rows", List.of());
            model.addAttribute("tiles", List.of());
            model.addAttribute("flashErrorTitle", "Cannot run this report");
            model.addAttribute("flashErrorMessage", e.getMessage());
        }
        return "report/reviews";
    }

    @PreAuthorize(STAFF_ROLE)
    @GetMapping("/reports/feedback")
    public String feedback(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Integer categoryId,
            @AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        LocalDate f = defaultFrom(from);
        LocalDate t = defaultTo(to);
        model.addAttribute("pageTitle", "Feedback Report");
        model.addAttribute("from", f);
        model.addAttribute("to", t);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("categoryOptions", reportService.feedbackCategoryOptions());
        model.addAttribute("columns", List.of(
                DataTableColumn.left("Category"), DataTableColumn.left("Status"), DataTableColumn.right("Count")));
        try {
            ReportService.FeedbackReport report = reportService.feedbackReport(f, t, categoryId, principal.userId());
            model.addAttribute("rows", report.rows());
            model.addAttribute("tiles", List.of(
                    new StatTile("Submissions", String.valueOf(report.totalSubmissions())),
                    new StatTile("Resolved / Closed", String.valueOf(report.resolvedOrClosed())),
                    new StatTile("Avg Resolution (days)", report.averageResolutionDays() == null ? "—" : String.format("%.1f", report.averageResolutionDays()))));
        } catch (ReportException e) {
            model.addAttribute("rows", List.of());
            model.addAttribute("tiles", List.of());
            model.addAttribute("flashErrorTitle", "Cannot run this report");
            model.addAttribute("flashErrorMessage", e.getMessage());
        }
        return "report/feedback";
    }

    @PreAuthorize(ADMIN_ROLE)
    @GetMapping("/reports/history")
    public String history(@RequestParam(required = false) String q, @RequestParam(required = false) String reportType,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "1") int page, Model model) {
        Page<ReportHistoryRow> result = reportService.reportHistory(q, reportType, from, to, page);

        model.addAttribute("pageTitle", "Report History");
        model.addAttribute("q", q);
        model.addAttribute("reportType", reportType);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("reportTypeOptions", ReportService.reportTypeOptions());
        model.addAttribute("columns", List.of(
                DataTableColumn.left("Requested by"), DataTableColumn.left("Report type"),
                DataTableColumn.left("Filters"), DataTableColumn.right("Generated at")));
        model.addAttribute("rows", result.getContent());

        long totalItems = result.getTotalElements();
        int totalPages = Math.max(result.getTotalPages(), 1);
        model.addAttribute("page", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalItems", totalItems);
        model.addAttribute("pageSize", ReportService.HISTORY_PAGE_SIZE);
        model.addAttribute("firstItem", totalItems == 0 ? 0 : (long) (page - 1) * ReportService.HISTORY_PAGE_SIZE + 1);
        model.addAttribute("lastItem", Math.min((long) page * ReportService.HISTORY_PAGE_SIZE, totalItems));

        return "report/history";
    }

    private static String blank(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
