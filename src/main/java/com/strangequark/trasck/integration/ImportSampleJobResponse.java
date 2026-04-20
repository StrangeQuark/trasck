package com.strangequark.trasck.integration;

public record ImportSampleJobResponse(
        ImportSampleResponse sample,
        ImportJobResponse importJob,
        ImportParseResponse parse,
        ImportTransformPresetResponse transformPreset,
        ImportMappingTemplateResponse mappingTemplate
) {
}
