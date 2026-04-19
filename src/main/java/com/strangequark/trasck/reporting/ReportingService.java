package com.strangequark.trasck.reporting;

import com.strangequark.trasck.access.PermissionService;
import com.strangequark.trasck.activity.WorkLog;
import com.strangequark.trasck.activity.WorkLogRepository;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.workitem.WorkItem;
import com.strangequark.trasck.workitem.WorkItemRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportingService {

    private final WorkItemRepository workItemRepository;
    private final WorkItemStatusHistoryRepository workItemStatusHistoryRepository;
    private final WorkItemAssignmentHistoryRepository workItemAssignmentHistoryRepository;
    private final WorkItemEstimateHistoryRepository workItemEstimateHistoryRepository;
    private final WorkLogRepository workLogRepository;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;

    public ReportingService(
            WorkItemRepository workItemRepository,
            WorkItemStatusHistoryRepository workItemStatusHistoryRepository,
            WorkItemAssignmentHistoryRepository workItemAssignmentHistoryRepository,
            WorkItemEstimateHistoryRepository workItemEstimateHistoryRepository,
            WorkLogRepository workLogRepository,
            CurrentUserService currentUserService,
            PermissionService permissionService
    ) {
        this.workItemRepository = workItemRepository;
        this.workItemStatusHistoryRepository = workItemStatusHistoryRepository;
        this.workItemAssignmentHistoryRepository = workItemAssignmentHistoryRepository;
        this.workItemEstimateHistoryRepository = workItemEstimateHistoryRepository;
        this.workLogRepository = workLogRepository;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
    }

    @Transactional(readOnly = true)
    public List<WorkItemStatusHistoryResponse> statusHistory(UUID workItemId) {
        WorkItem item = reportableWorkItem(workItemId);
        requireReportRead(item);
        return workItemStatusHistoryRepository.findByWorkItemIdOrderByChangedAtAsc(item.getId()).stream()
                .map(WorkItemStatusHistoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkItemAssignmentHistoryResponse> assignmentHistory(UUID workItemId) {
        WorkItem item = reportableWorkItem(workItemId);
        requireReportRead(item);
        return workItemAssignmentHistoryRepository.findByWorkItemIdOrderByChangedAtAsc(item.getId()).stream()
                .map(WorkItemAssignmentHistoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkItemEstimateHistoryResponse> estimateHistory(UUID workItemId) {
        WorkItem item = reportableWorkItem(workItemId);
        requireReportRead(item);
        return workItemEstimateHistoryRepository.findByWorkItemIdOrderByChangedAtAsc(item.getId()).stream()
                .map(WorkItemEstimateHistoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkLogSummaryResponse workLogSummary(UUID workItemId) {
        WorkItem item = reportableWorkItem(workItemId);
        requireReportRead(item);
        List<WorkLog> workLogs = workLogRepository.findByWorkItemIdAndDeletedAtIsNullOrderByWorkDateDescCreatedAtDesc(item.getId());
        Map<UUID, UserWorkLogAccumulator> userTotals = new LinkedHashMap<>();
        long totalMinutes = 0;
        for (WorkLog workLog : workLogs) {
            int minutes = workLog.getMinutesSpent() == null ? 0 : workLog.getMinutesSpent();
            totalMinutes += minutes;
            userTotals.computeIfAbsent(workLog.getUserId(), UserWorkLogAccumulator::new).add(minutes);
        }
        return new WorkLogSummaryResponse(
                item.getId(),
                workLogs.size(),
                totalMinutes,
                userTotals.values().stream()
                        .map(UserWorkLogAccumulator::response)
                        .toList()
        );
    }

    private WorkItem reportableWorkItem(UUID workItemId) {
        return workItemRepository.findByIdAndDeletedAtIsNull(workItemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work item not found"));
    }

    private void requireReportRead(WorkItem item) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireProjectPermission(actorId, item.getProjectId(), "report.read");
    }

    private static class UserWorkLogAccumulator {
        private final UUID userId;
        private int entryCount;
        private long totalMinutes;

        UserWorkLogAccumulator(UUID userId) {
            this.userId = userId;
        }

        void add(int minutes) {
            entryCount++;
            totalMinutes += minutes;
        }

        WorkLogSummaryResponse.UserWorkLogSummaryResponse response() {
            return new WorkLogSummaryResponse.UserWorkLogSummaryResponse(userId, entryCount, totalMinutes);
        }
    }
}
