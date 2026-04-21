package com.strangequark.trasck.access;

import java.util.UUID;

public record SystemAdminRequest(
        UUID userId
) {
}
