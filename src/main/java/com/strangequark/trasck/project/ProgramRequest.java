package com.strangequark.trasck.project;

public record ProgramRequest(
        String name,
        String description,
        String status,
        Object roadmapConfig,
        Object reportConfig
) {
}
