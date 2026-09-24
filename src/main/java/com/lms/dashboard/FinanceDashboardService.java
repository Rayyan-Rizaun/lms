package com.lms.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.common.domain.AppealStatus;
import com.lms.common.domain.FineRepository;
import com.lms.common.web.ChartData;
import com.lms.common.web.ChartDataset;
import com.lms.common.web.StatTile;
import com.lms.dashboard.dto.FinanceDashboardView;
import com.lms.dashboard.dto.OutstandingFineRow;
import com.lms.fine.FineService;
import com.lms.fine.dto.AppealQueueRow;

@Service
@Transactional(readOnly = true)
public class FinanceDashboardService {

    private static final String FINANCE_ROLE = "hasAuthority('Finance Officer')";

    private final FineRepository fines;
    private final FineService fineService;

    public FinanceDashboardService(FineRepository fines, FineService fineService) {
        this.fines = fines;
        this.fineService = fineService;
    }

    @PreAuthorize(FINANCE_ROLE)
    public FinanceDashboardView view() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        BigDecimal outstanding = fines.outstandingBalanceTotal();
        BigDecimal collectedThisMonth = fines.collectedBetween(monthStart, now.plusSeconds(1));
        BigDecimal waivedThisMonth = fines.waivedBetween(monthStart, now.plusSeconds(1));
        long openAppeals = fines.countDistinctByAppealsStatus(AppealStatus.Pending);

        List<StatTile> tiles = List.of(
                new StatTile("Outstanding Fines", "LKR " + outstanding, outstanding.signum() > 0),
                new StatTile("Collected This Month", "LKR " + collectedThisMonth),
                new StatTile("Waived This Month", "LKR " + waivedThisMonth),
                new StatTile("Open Appeals", String.valueOf(openAppeals), openAppeals > 0));

        ChartData finesByType = finesByTypeChart(now);
        ChartData collectionsPerWeek = collectionsPerWeekChart(now);

        List<OutstandingFineRow> largestOutstandingFines = fines.topOutstandingFines().stream()
                .map(row -> new OutstandingFineRow((Integer) row[0], (String) row[1], (String) row[2], (BigDecimal) row[3]))
                .toList();

        List<AppealQueueRow> pendingAppeals = fineService.pendingAppeals().stream()
                .limit(5)
                .toList();

        return new FinanceDashboardView(tiles, finesByType, collectionsPerWeek, largestOutstandingFines, pendingAppeals);
    }

    private ChartData finesByTypeChart(LocalDateTime now) {
        List<String> labels = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        for (Object[] row : fines.finesByType(now.minusYears(5), now.plusSeconds(1), null)) {
            labels.add((String) row[0]);
            values.add((BigDecimal) row[1]);
        }
        return new ChartData(labels, List.of(new ChartDataset("Assessed", values)));
    }

    private ChartData collectionsPerWeekChart(LocalDateTime now) {
        LocalDate today = LocalDate.now();
        LocalDate currentWeekStart = today.minusDays((today.getDayOfWeek().getValue() % 7));
        LinkedHashMap<LocalDate, BigDecimal> byWeek = new LinkedHashMap<>();
        for (int i = 7; i >= 0; i--) {
            byWeek.put(currentWeekStart.minusWeeks(i), BigDecimal.ZERO);
        }
        LocalDateTime from = currentWeekStart.minusWeeks(7).atStartOfDay();
        LocalDateTime to = now.plusSeconds(1);
        for (Object[] row : fines.collectionsPerWeek(from, to)) {
            LocalDate weekStart = ((java.sql.Date) row[0]).toLocalDate();
            if (byWeek.containsKey(weekStart)) {
                byWeek.put(weekStart, (BigDecimal) row[1]);
            }
        }
        List<String> labels = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        for (Map.Entry<LocalDate, BigDecimal> entry : byWeek.entrySet()) {
            labels.add(entry.getKey().getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + entry.getKey().getDayOfMonth());
            values.add(entry.getValue());
        }
        return new ChartData(labels, List.of(new ChartDataset("Collected", values)));
    }
}
