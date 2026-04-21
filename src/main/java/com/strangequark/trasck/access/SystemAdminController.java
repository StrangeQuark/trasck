package com.strangequark.trasck.access;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SystemAdminController {

    private final SystemAdminService systemAdminService;

    public SystemAdminController(SystemAdminService systemAdminService) {
        this.systemAdminService = systemAdminService;
    }

    @GetMapping("/system-admins")
    public List<SystemAdminResponse> listSystemAdmins() {
        return systemAdminService.listSystemAdmins();
    }

    @PostMapping("/system-admins")
    public ResponseEntity<SystemAdminResponse> grantSystemAdmin(@RequestBody SystemAdminRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(systemAdminService.grantSystemAdmin(request));
    }

    @DeleteMapping("/system-admins/{userId}")
    public SystemAdminResponse revokeSystemAdmin(@PathVariable UUID userId) {
        return systemAdminService.revokeSystemAdmin(userId);
    }
}
