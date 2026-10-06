package com.lms.dashboard.dto;

import java.util.List;

import com.lms.common.web.ChartData;
import com.lms.common.web.StatTile;
import com.lms.fine.dto.AppealQueueRow;

public record FinanceDashboardView(List<StatTile> tiles, ChartData finesByType, ChartData collectionsPerWeek,
        List<OutstandingFineRow> largestOutstandingFines, List<AppealQueueRow> pendingAppeals) {
}
