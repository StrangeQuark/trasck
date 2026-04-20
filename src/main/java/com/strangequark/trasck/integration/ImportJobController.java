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

    @PostMapping("/import-jobs/{importJobId}/records")
    public ResponseEntity<ImportJobRecordResponse> createRecord(
            @PathVariable UUID importJobId,
            @RequestBody ImportJobRecordRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(importJobService.createRecord(importJobId, request));
    }
}
