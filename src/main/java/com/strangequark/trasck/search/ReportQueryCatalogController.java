package com.strangequark.trasck.search;

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
public class ReportQueryCatalogController {

    private final ReportQueryCatalogService reportQueryCatalogService;

    public ReportQueryCatalogController(ReportQueryCatalogService reportQueryCatalogService) {
        this.reportQueryCatalogService = reportQueryCatalogService;
    }

    @GetMapping("/workspaces/{workspaceId}/report-query-catalog")
    public List<ReportQueryCatalogResponse> list(@PathVariable UUID workspaceId) {
        return reportQueryCatalogService.list(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/report-query-catalog")
    public ResponseEntity<ReportQueryCatalogResponse> create(
            @PathVariable UUID workspaceId,
            @RequestBody ReportQueryCatalogRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reportQueryCatalogService.create(workspaceId, request));
    }

    @GetMapping("/report-query-catalog/{queryId}")
    public ReportQueryCatalogResponse get(@PathVariable UUID queryId) {
        return reportQueryCatalogService.get(queryId);
    }

    @PatchMapping("/report-query-catalog/{queryId}")
    public ReportQueryCatalogResponse update(
            @PathVariable UUID queryId,
            @RequestBody ReportQueryCatalogRequest request
    ) {
        return reportQueryCatalogService.update(queryId, request);
    }

    @DeleteMapping("/report-query-catalog/{queryId}")
    public ResponseEntity<Void> delete(@PathVariable UUID queryId) {
        reportQueryCatalogService.delete(queryId);
        return ResponseEntity.noContent().build();
    }
}
