package com.strangequark.trasck.setup;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/setup")
public class InitialSetupController {

    private final InitialSetupService initialSetupService;

    public InitialSetupController(InitialSetupService initialSetupService) {
        this.initialSetupService = initialSetupService;
    }

    @GetMapping("/status")
    public InitialSetupStatusResponse setupStatus() {
        return initialSetupService.status();
    }

    @PostMapping
    public ResponseEntity<InitialSetupResponse> createInitialSetup(@RequestBody InitialSetupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(initialSetupService.createInitialSetup(request));
    }
}
