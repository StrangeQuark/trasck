package com.strangequark.trasck.setup;

public record InitialSetupStatusResponse(
        boolean available,
        boolean completed
) {
}
