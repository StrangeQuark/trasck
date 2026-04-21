package com.strangequark.trasck.access;

import java.util.List;

public record RolePermissionPreviewRequest(
        List<String> permissionKeys
) {
}
