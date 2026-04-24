package com.strangequark.trasck.identity;

import java.util.List;

public record AuthContextResponse(
        AuthUserResponse user,
        List<AuthWorkspaceContextResponse> workspaces,
        List<AuthProjectContextResponse> projects,
        AuthWorkspaceContextResponse defaultWorkspace,
        AuthProjectContextResponse defaultProject,
        boolean systemAdmin
) {
}
