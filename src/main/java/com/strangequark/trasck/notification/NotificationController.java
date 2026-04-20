package com.strangequark.trasck.notification;

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
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/workspaces/{workspaceId}/notifications")
    public List<NotificationResponse> listNotifications(@PathVariable UUID workspaceId) {
        return notificationService.listNotifications(workspaceId);
    }

    @PatchMapping("/notifications/{notificationId}/read")
    public NotificationResponse markRead(@PathVariable UUID notificationId) {
        return notificationService.markRead(notificationId);
    }

    @GetMapping("/workspaces/{workspaceId}/notification-preferences")
    public List<NotificationPreferenceResponse> listPreferences(@PathVariable UUID workspaceId) {
        return notificationService.listPreferences(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/notification-preferences")
    public ResponseEntity<NotificationPreferenceResponse> upsertPreference(
            @PathVariable UUID workspaceId,
            @RequestBody NotificationPreferenceRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(notificationService.upsertPreference(workspaceId, request));
    }

    @PatchMapping("/notification-preferences/{preferenceId}")
    public NotificationPreferenceResponse updatePreference(
            @PathVariable UUID preferenceId,
            @RequestBody NotificationPreferenceRequest request
    ) {
        return notificationService.updatePreference(preferenceId, request);
    }

    @DeleteMapping("/notification-preferences/{preferenceId}")
    public ResponseEntity<Void> deletePreference(@PathVariable UUID preferenceId) {
        notificationService.deletePreference(preferenceId);
        return ResponseEntity.noContent().build();
    }
}
