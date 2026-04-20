package com.strangequark.trasck.automation;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class AutomationWorkerHealthId implements Serializable {
    private UUID workspaceId;
    private String workerType;

    public AutomationWorkerHealthId() {
    }

    public AutomationWorkerHealthId(UUID workspaceId, String workerType) {
        this.workspaceId = workspaceId;
        this.workerType = workerType;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AutomationWorkerHealthId that)) {
            return false;
        }
        return Objects.equals(workspaceId, that.workspaceId)
                && Objects.equals(workerType, that.workerType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workspaceId, workerType);
    }
}
