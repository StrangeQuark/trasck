package com.strangequark.trasck.workitem;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class WorkItemSequenceService {

    private final JdbcTemplate jdbcTemplate;

    public WorkItemSequenceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long nextWorkspaceSequence(UUID workspaceId) {
        return nextValue("""
                insert into workspace_work_item_sequences (workspace_id, next_value)
                values (?, 2)
                on conflict (workspace_id) do update
                    set next_value = workspace_work_item_sequences.next_value + 1
                returning next_value - 1
                """, workspaceId);
    }

    public long nextProjectSequence(UUID workspaceId, UUID projectId) {
        return nextValue("""
                insert into project_work_item_sequences (project_id, workspace_id, next_value)
                values (?, ?, 2)
                on conflict (project_id) do update
                    set next_value = project_work_item_sequences.next_value + 1
                returning next_value - 1
                """, projectId, workspaceId);
    }

    private long nextValue(String sql, Object... args) {
        Long next = jdbcTemplate.queryForObject(sql, Long.class, args);
        if (next == null) {
            throw new IllegalStateException("Could not allocate work item sequence");
        }
        return next;
    }
}
