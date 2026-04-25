package com.strangequark.trasck.organization;

public record OrganizationRequest(
        String name,
        String slug,
        String status
) {
}
