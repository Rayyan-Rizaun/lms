package com.lms.dashboard.dto;

import java.util.List;

import com.lms.admin.dto.AuditLogRow;
import com.lms.common.web.ChartData;
import com.lms.common.web.StatTile;
import com.lms.user.dto.PendingMemberRow;

public record AdminDashboardView(List<StatTile> tiles, ChartData loansPerMonth, ChartData copiesByStatus,
        ChartData finesAssessedVsCollected, List<AuditLogRow> recentAuditEntries,
        List<PendingMemberRow> oldestPendingApprovals) {
}
