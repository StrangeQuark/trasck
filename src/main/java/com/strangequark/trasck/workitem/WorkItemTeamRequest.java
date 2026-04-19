package com.strangequark.trasck.workitem;

import java.util.UUID;

public record WorkItemTeamRequest(
        UUID teamId,
        Boolean clearTeam
) {
}
