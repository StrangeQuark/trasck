package com.strangequark.trasck.integration;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ImportJobController {

    private final ImportJobService importJobService;

    public ImportJobController(ImportJobService importJobService) {
        this.importJobService = importJobService;
    }

    @GetMapping("/workspaces/{workspaceId}/import-jobs")
    public List<ImportJobResponse> listImportJobs(@PathVariable UUID workspaceId) {
        return importJobService.listImportJobs(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/import-jobs")
    public ResponseEntity<ImportJobResponse> createImportJob(
            @PathVariable UUID workspaceId,
            @RequestBody ImportJobRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(importJobService.createImportJob(workspaceId, request));
    }

    @GetMapping("/workspaces/{workspaceId}/import-mapping-templates")
    public List<ImportMappingTemplateResponse> listMappingTemplates(@PathVariable UUID workspaceId) {
        return importJobService.listMappingTemplates(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/import-mapping-templates")
    public ResponseEntity<ImportMappingTemplateResponse> createMappingTemplate(
            @PathVariable UUID workspaceId,
            @RequestBody ImportMappingTemplateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(importJobService.createMappingTemplate(workspaceId, request));
    }

    @GetMapping("/workspaces/{workspaceId}/import-transform-presets")
    public List<ImportTransformPresetResponse> listTransformPresets(@PathVariable UUID workspaceId) {
        return importJobService.listTransformPresets(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/import-transform-presets")
    public ResponseEntity<ImportTransformPresetResponse> createTransformPreset(
            @PathVariable UUID workspaceId,
            @RequestBody ImportTransformPresetRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(importJobService.createTransformPreset(workspaceId, request));
    }

    @GetMapping("/import-transform-presets/{presetId}")
    public ImportTransformPresetResponse getTransformPreset(@PathVariable UUID presetId) {
        return importJobService.getTransformPreset(presetId);
    }

    @GetMapping("/import-transform-presets/{presetId}/versions")
    public List<ImportTransformPresetVersionResponse> listTransformPresetVersions(@PathVariable UUID presetId) {
        return importJobService.listTransformPresetVersions(presetId);
    }

    @PatchMapping("/import-transform-presets/{presetId}")
    public ImportTransformPresetResponse updateTransformPreset(
            @PathVariable UUID presetId,
            @RequestBody ImportTransformPresetRequest request
    ) {
        return importJobService.updateTransformPreset(presetId, request);
    }

    @DeleteMapping("/import-transform-presets/{presetId}")
    public ResponseEntity<Void> deleteTransformPreset(@PathVariable UUID presetId) {
        importJobService.deleteTransformPreset(presetId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/import-mapping-templates/{mappingTemplateId}")
    public ImportMappingTemplateResponse updateMappingTemplate(
            @PathVariable UUID mappingTemplateId,
            @RequestBody ImportMappingTemplateRequest request
    ) {
        return importJobService.updateMappingTemplate(mappingTemplateId, request);
    }

    @DeleteMapping("/import-mapping-templates/{mappingTemplateId}")
    public ResponseEntity<Void> deleteMappingTemplate(@PathVariable UUID mappingTemplateId) {
        importJobService.deleteMappingTemplate(mappingTemplateId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/import-mapping-templates/{mappingTemplateId}/value-lookups")
    public List<ImportMappingValueLookupResponse> listValueLookups(@PathVariable UUID mappingTemplateId) {
        return importJobService.listValueLookups(mappingTemplateId);
    }

    @PostMapping("/import-mapping-templates/{mappingTemplateId}/value-lookups")
    public ResponseEntity<ImportMappingValueLookupResponse> createValueLookup(
            @PathVariable UUID mappingTemplateId,
            @RequestBody ImportMappingValueLookupRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(importJobService.createValueLookup(mappingTemplateId, request));
    }

    @PatchMapping("/import-mapping-templates/{mappingTemplateId}/value-lookups/{lookupId}")
    public ImportMappingValueLookupResponse updateValueLookup(
            @PathVariable UUID mappingTemplateId,
            @PathVariable UUID lookupId,
            @RequestBody ImportMappingValueLookupRequest request
    ) {
        return importJobService.updateValueLookup(mappingTemplateId, lookupId, request);
    }

    @DeleteMapping("/import-mapping-templates/{mappingTemplateId}/value-lookups/{lookupId}")
    public ResponseEntity<Void> deleteValueLookup(
            @PathVariable UUID mappingTemplateId,
            @PathVariable UUID lookupId
    ) {
        importJobService.deleteValueLookup(mappingTemplateId, lookupId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/import-mapping-templates/{mappingTemplateId}/type-translations")
    public List<ImportMappingTypeTranslationResponse> listTypeTranslations(@PathVariable UUID mappingTemplateId) {
        return importJobService.listTypeTranslations(mappingTemplateId);
    }

    @PostMapping("/import-mapping-templates/{mappingTemplateId}/type-translations")
    public ResponseEntity<ImportMappingTypeTranslationResponse> createTypeTranslation(
            @PathVariable UUID mappingTemplateId,
            @RequestBody ImportMappingTypeTranslationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(importJobService.createTypeTranslation(mappingTemplateId, request));
    }

    @PatchMapping("/import-mapping-templates/{mappingTemplateId}/type-translations/{translationId}")
    public ImportMappingTypeTranslationResponse updateTypeTranslation(
            @PathVariable UUID mappingTemplateId,
            @PathVariable UUID translationId,
            @RequestBody ImportMappingTypeTranslationRequest request
    ) {
        return importJobService.updateTypeTranslation(mappingTemplateId, translationId, request);
    }

    @DeleteMapping("/import-mapping-templates/{mappingTemplateId}/type-translations/{translationId}")
    public ResponseEntity<Void> deleteTypeTranslation(
            @PathVariable UUID mappingTemplateId,
            @PathVariable UUID translationId
    ) {
        importJobService.deleteTypeTranslation(mappingTemplateId, translationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/import-mapping-templates/{mappingTemplateId}/status-translations")
    public List<ImportMappingStatusTranslationResponse> listStatusTranslations(@PathVariable UUID mappingTemplateId) {
        return importJobService.listStatusTranslations(mappingTemplateId);
    }

    @PostMapping("/import-mapping-templates/{mappingTemplateId}/status-translations")
    public ResponseEntity<ImportMappingStatusTranslationResponse> createStatusTranslation(
            @PathVariable UUID mappingTemplateId,
            @RequestBody ImportMappingStatusTranslationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(importJobService.createStatusTranslation(mappingTemplateId, request));
    }

    @PatchMapping("/import-mapping-templates/{mappingTemplateId}/status-translations/{translationId}")
    public ImportMappingStatusTranslationResponse updateStatusTranslation(
            @PathVariable UUID mappingTemplateId,
            @PathVariable UUID translationId,
            @RequestBody ImportMappingStatusTranslationRequest request
    ) {
        return importJobService.updateStatusTranslation(mappingTemplateId, translationId, request);
    }

    @DeleteMapping("/import-mapping-templates/{mappingTemplateId}/status-translations/{translationId}")
    public ResponseEntity<Void> deleteStatusTranslation(
            @PathVariable UUID mappingTemplateId,
            @PathVariable UUID translationId
    ) {
        importJobService.deleteStatusTranslation(mappingTemplateId, translationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/import-jobs/{importJobId}")
    public ImportJobResponse getImportJob(@PathVariable UUID importJobId) {
        return importJobService.getImportJob(importJobId);
    }

    @PostMapping("/import-jobs/{importJobId}/start")
    public ImportJobResponse startImportJob(@PathVariable UUID importJobId) {
        return importJobService.startImportJob(importJobId);
    }

    @PostMapping("/import-jobs/{importJobId}/complete")
    public ImportJobResponse completeImportJob(@PathVariable UUID importJobId) {
        return importJobService.completeImportJob(importJobId);
    }

    @PostMapping("/import-jobs/{importJobId}/fail")
    public ImportJobResponse failImportJob(@PathVariable UUID importJobId) {
        return importJobService.failImportJob(importJobId);
    }

    @PostMapping("/import-jobs/{importJobId}/cancel")
    public ImportJobResponse cancelImportJob(@PathVariable UUID importJobId) {
        return importJobService.cancelImportJob(importJobId);
    }

    @PostMapping("/import-jobs/{importJobId}/parse")
    public ImportParseResponse parseImportJob(
            @PathVariable UUID importJobId,
            @RequestBody ImportParseRequest request
    ) {
        return importJobService.parse(importJobId, request);
    }

    @PostMapping("/import-jobs/{importJobId}/materialize")
    public ImportMaterializeResponse materializeImportJob(
            @PathVariable UUID importJobId,
            @RequestBody ImportMaterializeRequest request
    ) {
        return importJobService.materialize(importJobId, request);
    }

    @GetMapping("/import-jobs/{importJobId}/records")
    public List<ImportJobRecordResponse> listRecords(@PathVariable UUID importJobId) {
        return importJobService.listRecords(importJobId);
    }

    @GetMapping("/import-jobs/{importJobId}/materialization-runs")
    public List<ImportMaterializationRunResponse> listMaterializationRuns(@PathVariable UUID importJobId) {
        return importJobService.listMaterializationRuns(importJobId);
    }

    @PostMapping("/import-jobs/{importJobId}/records")
    public ResponseEntity<ImportJobRecordResponse> createRecord(
            @PathVariable UUID importJobId,
            @RequestBody ImportJobRecordRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(importJobService.createRecord(importJobId, request));
    }
}
