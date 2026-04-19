package com.strangequark.trasck.customfield;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CustomFieldController {

    private final CustomFieldService customFieldService;

    public CustomFieldController(CustomFieldService customFieldService) {
        this.customFieldService = customFieldService;
    }

    @GetMapping("/workspaces/{workspaceId}/custom-fields")
    public List<CustomFieldResponse> listCustomFields(
            @PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "false") boolean includeArchived
    ) {
        return customFieldService.listCustomFields(workspaceId, includeArchived);
    }

    @PostMapping("/workspaces/{workspaceId}/custom-fields")
    public ResponseEntity<CustomFieldResponse> createCustomField(
            @PathVariable UUID workspaceId,
            @RequestBody CustomFieldRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customFieldService.createCustomField(workspaceId, request));
    }

    @GetMapping("/custom-fields/{customFieldId}")
    public CustomFieldResponse getCustomField(@PathVariable UUID customFieldId) {
        return customFieldService.getCustomField(customFieldId);
    }

    @PatchMapping("/custom-fields/{customFieldId}")
    public CustomFieldResponse updateCustomField(
            @PathVariable UUID customFieldId,
            @RequestBody CustomFieldRequest request
    ) {
        return customFieldService.updateCustomField(customFieldId, request);
    }

    @DeleteMapping("/custom-fields/{customFieldId}")
    public ResponseEntity<Void> archiveCustomField(@PathVariable UUID customFieldId) {
        customFieldService.archiveCustomField(customFieldId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/custom-fields/{customFieldId}/contexts")
    public List<CustomFieldContextResponse> listContexts(@PathVariable UUID customFieldId) {
        return customFieldService.listContexts(customFieldId);
    }

    @PostMapping("/custom-fields/{customFieldId}/contexts")
    public ResponseEntity<CustomFieldContextResponse> createContext(
            @PathVariable UUID customFieldId,
            @RequestBody CustomFieldContextRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customFieldService.createContext(customFieldId, request));
    }

    @PatchMapping("/custom-fields/{customFieldId}/contexts/{contextId}")
    public CustomFieldContextResponse updateContext(
            @PathVariable UUID customFieldId,
            @PathVariable UUID contextId,
            @RequestBody CustomFieldContextRequest request
    ) {
        return customFieldService.updateContext(customFieldId, contextId, request);
    }

    @DeleteMapping("/custom-fields/{customFieldId}/contexts/{contextId}")
    public ResponseEntity<Void> deleteContext(
            @PathVariable UUID customFieldId,
            @PathVariable UUID contextId
    ) {
        customFieldService.deleteContext(customFieldId, contextId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/work-items/{workItemId}/custom-fields")
    public List<CustomFieldValueResponse> listValues(@PathVariable UUID workItemId) {
        return customFieldService.listValues(workItemId);
    }

    @PutMapping("/work-items/{workItemId}/custom-fields/{customFieldId}")
    public CustomFieldValueResponse setValue(
            @PathVariable UUID workItemId,
            @PathVariable UUID customFieldId,
            @RequestBody CustomFieldValueRequest request
    ) {
        return customFieldService.setValue(workItemId, customFieldId, request);
    }

    @DeleteMapping("/work-items/{workItemId}/custom-fields/{customFieldId}")
    public ResponseEntity<Void> deleteValue(
            @PathVariable UUID workItemId,
            @PathVariable UUID customFieldId
    ) {
        customFieldService.deleteValue(workItemId, customFieldId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/workspaces/{workspaceId}/screens")
    public List<ScreenResponse> listScreens(@PathVariable UUID workspaceId) {
        return customFieldService.listScreens(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/screens")
    public ResponseEntity<ScreenResponse> createScreen(
            @PathVariable UUID workspaceId,
            @RequestBody ScreenRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customFieldService.createScreen(workspaceId, request));
    }

    @GetMapping("/screens/{screenId}")
    public ScreenResponse getScreen(@PathVariable UUID screenId) {
        return customFieldService.getScreen(screenId);
    }

    @PatchMapping("/screens/{screenId}")
    public ScreenResponse updateScreen(
            @PathVariable UUID screenId,
            @RequestBody ScreenRequest request
    ) {
        return customFieldService.updateScreen(screenId, request);
    }

    @DeleteMapping("/screens/{screenId}")
    public ResponseEntity<Void> deleteScreen(@PathVariable UUID screenId) {
        customFieldService.deleteScreen(screenId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/screens/{screenId}/fields")
    public List<ScreenFieldResponse> listScreenFields(@PathVariable UUID screenId) {
        return customFieldService.listScreenFields(screenId);
    }

    @PostMapping("/screens/{screenId}/fields")
    public ResponseEntity<ScreenFieldResponse> addScreenField(
            @PathVariable UUID screenId,
            @RequestBody ScreenFieldRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customFieldService.addScreenField(screenId, request));
    }

    @PatchMapping("/screens/{screenId}/fields/{screenFieldId}")
    public ScreenFieldResponse updateScreenField(
            @PathVariable UUID screenId,
            @PathVariable UUID screenFieldId,
            @RequestBody ScreenFieldRequest request
    ) {
        return customFieldService.updateScreenField(screenId, screenFieldId, request);
    }

    @DeleteMapping("/screens/{screenId}/fields/{screenFieldId}")
    public ResponseEntity<Void> deleteScreenField(
            @PathVariable UUID screenId,
            @PathVariable UUID screenFieldId
    ) {
        customFieldService.deleteScreenField(screenId, screenFieldId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/screens/{screenId}/assignments")
    public List<ScreenAssignmentResponse> listScreenAssignments(@PathVariable UUID screenId) {
        return customFieldService.listScreenAssignments(screenId);
    }

    @PostMapping("/screens/{screenId}/assignments")
    public ResponseEntity<ScreenAssignmentResponse> addScreenAssignment(
            @PathVariable UUID screenId,
            @RequestBody ScreenAssignmentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customFieldService.addScreenAssignment(screenId, request));
    }

    @PatchMapping("/screens/{screenId}/assignments/{assignmentId}")
    public ScreenAssignmentResponse updateScreenAssignment(
            @PathVariable UUID screenId,
            @PathVariable UUID assignmentId,
            @RequestBody ScreenAssignmentRequest request
    ) {
        return customFieldService.updateScreenAssignment(screenId, assignmentId, request);
    }

    @DeleteMapping("/screens/{screenId}/assignments/{assignmentId}")
    public ResponseEntity<Void> deleteScreenAssignment(
            @PathVariable UUID screenId,
            @PathVariable UUID assignmentId
    ) {
        customFieldService.deleteScreenAssignment(screenId, assignmentId);
        return ResponseEntity.noContent().build();
    }
}
