package com.lms.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.admin.dto.AuditLogRow;
import com.lms.common.domain.AuditLog;
import com.lms.common.domain.AuditLogRepository;
import com.lms.common.domain.BookCopyRepository;
import com.lms.common.domain.BookReviewRepository;
import com.lms.common.domain.FineRepository;
import com.lms.common.domain.LoanRepository;
import com.lms.common.domain.LoanStatus;
import com.lms.common.domain.MemberRepository;
import com.lms.common.domain.ReviewStatus;
import com.lms.common.web.ChartData;
import com.lms.common.web.ChartDataset;
import com.lms.common.web.StatTile;
import com.lms.dashboard.dto.AdminDashboardView;
import com.lms.user.MemberAdminService;
import com.lms.user.dto.PendingMemberRow;

@Service
@Transactional(readOnly = true)
public class AdminDashboardService {

    private static final String ADMIN_ROLE = "hasAuthority('Library Administrator')";

    private final MemberRepository members;
    private final LoanRepository loans;
    private final FineRepository fines;
    private final BookCopyRepository copies;
    private final BookReviewRepository reviews;
    private final AuditLogRepository auditLogs;
    private final MemberAdminService memberAdminService;

    public AdminDashboardService(MemberRepository members, LoanRepository loans, FineRepository fines,
            BookCopyRepository copies, BookReviewRepository reviews, AuditLogRepository auditLogs,
            MemberAdminService memberAdminService) {
        this.members = members;
        this.loans = loans;
        this.fines = fines;
        this.copies = copies;
        this.reviews = reviews;
        this.auditLogs = auditLogs;
        this.memberAdminService = memberAdminService;
    }

    @PreAuthorize(ADMIN_ROLE)
    public AdminDashboardView view() {
        LocalDateTime now = LocalDateTime.now();

        long totalMembers = members.count();
        long activeLoans = loans.countByStatus(LoanStatus.Active);
        long overdueLoans = loans.countByStatusAndDueAtBefore(LoanStatus.Active, now);
        BigDecimal outstandingFines = fines.outstandingBalanceTotal();
        long pendingApprovals = memberAdminService.pendingCount();
        long pendingModerations = reviews.countByStatusIn(List.of(ReviewStatus.Pending, ReviewStatus.Hidden));

        List<StatTile> tiles = List.of(
                new StatTile("Total Members", String.valueOf(totalMembers)),
                new StatTile("Active Loans", String.valueOf(activeLoans)),
                new StatTile("Overdue Loans", String.valueOf(overdueLoans), overdueLoans > 0),
                new StatTile("Outstanding Fines", "LKR " + outstandingFines, outstandingFines.signum() > 0),
                new StatTile("Pending Approvals", String.valueOf(pendingApprovals), pendingApprovals > 0),
                new StatTile("Pending Moderations", String.valueOf(pendingModerations), pendingModerations > 0));

        ChartData loansPerMonth = loansPerMonthChart(now);
        ChartData copiesByStatus = copiesByStatusChart();
        ChartData finesAssessedVsCollected = finesAssessedVsCollectedChart(now);

        List<AuditLogRow> recentAuditEntries = auditLogs
                .findAll(PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "occurredAt")))
                .getContent().stream()
                .map(this::toRow)
                .toList();

        List<PendingMemberRow> oldestPendingApprovals = memberAdminService.pendingMembers().stream()
                .limit(5)
                .toList();

        return new AdminDashboardView(tiles, loansPerMonth, copiesByStatus, finesAssessedVsCollected,
                recentAuditEntries, oldestPendingApprovals);
    }

    private AuditLogRow toRow(AuditLog a) {
        String userName = a.getUser() == null ? "System" : a.getUser().getFirstName() + " " + a.getUser().getLastName();
        return new AuditLogRow(a.getAuditId(), userName, a.getActionName(), a.getEntityName(), a.getEntityId(), a.getOccurredAt());
    }

    private ChartData loansPerMonthChart(LocalDateTime now) {
        YearMonth currentMonth = YearMonth.from(now);
        LinkedHashMap<YearMonth, Integer> byMonth = new LinkedHashMap<>();
        for (int i = 5; i >= 0; i--) {
            byMonth.put(currentMonth.minusMonths(i), 0);
        }
        LocalDateTime from = currentMonth.minusMonths(5).atDay(1).atStartOfDay();
        LocalDateTime to = now.plusSeconds(1);
        for (Object[] row : loans.loansPerMonth(from, to)) {
            YearMonth month = YearMonth.from(((java.sql.Date) row[0]).toLocalDate());
            if (byMonth.containsKey(month)) {
                byMonth.put(month, ((Number) row[1]).intValue());
            }
        }
        List<String> labels = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        for (Map.Entry<YearMonth, Integer> entry : byMonth.entrySet()) {
            labels.add(entry.getKey().getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + entry.getKey().getYear());
            values.add(BigDecimal.valueOf(entry.getValue()));
        }
        return new ChartData(labels, List.of(new ChartDataset("Loans issued", values)));
    }

    private ChartData copiesByStatusChart() {
        List<String> labels = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        for (Object[] row : copies.countGroupedByStatus()) {
            labels.add((String) row[0]);
            values.add(BigDecimal.valueOf(((Number) row[1]).longValue()));
        }
        return new ChartData(labels, List.of(new ChartDataset("Copies", values)));
    }

    private ChartData finesAssessedVsCollectedChart(LocalDateTime now) {
        YearMonth currentMonth = YearMonth.from(now);
        LinkedHashMap<YearMonth, BigDecimal[]> byMonth = new LinkedHashMap<>();
        for (int i = 5; i >= 0; i--) {
            byMonth.put(currentMonth.minusMonths(i), new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
        }
        for (Map.Entry<YearMonth, BigDecimal[]> entry : byMonth.entrySet()) {
            LocalDateTime from = entry.getKey().atDay(1).atStartOfDay();
            LocalDateTime to = entry.getKey().plusMonths(1).atDay(1).atStartOfDay();
            for (Object[] row : fines.finesByType(from, to, null)) {
                entry.getValue()[0] = entry.getValue()[0].add((BigDecimal) row[1]);
                entry.getValue()[1] = entry.getValue()[1].add((BigDecimal) row[2]);
            }
        }
        List<String> labels = new ArrayList<>();
        List<BigDecimal> assessed = new ArrayList<>();
        List<BigDecimal> collected = new ArrayList<>();
        for (Map.Entry<YearMonth, BigDecimal[]> entry : byMonth.entrySet()) {
            labels.add(entry.getKey().getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + entry.getKey().getYear());
            assessed.add(entry.getValue()[0]);
            collected.add(entry.getValue()[1]);
        }
        return new ChartData(labels, List.of(new ChartDataset("Assessed", assessed), new ChartDataset("Collected", collected)));
    }
}
