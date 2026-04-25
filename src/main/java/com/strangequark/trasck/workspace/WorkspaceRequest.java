package com.strangequark.trasck.workspace;

public record WorkspaceRequest(
        String name,
        String key,
        String timezone,
        String locale,
        Boolean anonymousReadEnabled,
        String status
) {
}
