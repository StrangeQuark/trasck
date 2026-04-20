package com.strangequark.trasck.integration;

public record ImportMaterializationRerunRequest(
        Integer limit,
        Boolean updateExisting
) {
}
