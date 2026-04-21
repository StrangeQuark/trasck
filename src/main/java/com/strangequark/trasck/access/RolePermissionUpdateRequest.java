package com.strangequark.trasck.access;

import java.util.List;

public record RolePermissionUpdateRequest(
        List<String> permissionKeys,
        Boolean confirmed,
        String previewToken
) {
}
