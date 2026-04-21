package com.strangequark.trasck.access;

import java.util.UUID;

public record PermissionResponse(
        UUID id,
        String key,
        String name,
        String description,
        String category
) {
    static PermissionResponse from(Permission permission) {
        return new PermissionResponse(
                permission.getId(),
                permission.getKey(),
                permission.getName(),
                permission.getDescription(),
                permission.getCategory()
        );
    }
}
