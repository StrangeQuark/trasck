package com.strangequark.trasck.access;

import java.util.List;
import java.util.UUID;

public record RoleImpactPreviewResponse(
        UUID roleId,
        List<String> currentPermissionKeys,
        List<String> requestedPermissionKeys,
        List<String> addedPermissionKeys,
        List<String> removedPermissionKeys,
        RoleImpactSummary impactSummary,
        boolean removesAdministrativePermission,
        boolean confirmationRequired,
        String confirmationText,
        String previewToken
) {
}
