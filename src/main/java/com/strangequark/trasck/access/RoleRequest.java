package com.strangequark.trasck.access;

import java.util.List;

public record RoleRequest(
        String key,
        String name,
        String description,
        List<String> permissionKeys
) {
}
