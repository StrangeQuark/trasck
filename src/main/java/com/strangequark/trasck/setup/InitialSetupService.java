package com.strangequark.trasck.setup;

import com.strangequark.trasck.access.SystemAdmin;
import com.strangequark.trasck.access.SystemAdminRepository;
import com.strangequark.trasck.identity.User;
import com.strangequark.trasck.identity.UserRepository;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InitialSetupService {

    private final IdentitySetupService identitySetupService;
    private final UserRepository userRepository;
    private final SystemAdminRepository systemAdminRepository;

    public InitialSetupService(
            IdentitySetupService identitySetupService,
            UserRepository userRepository,
            SystemAdminRepository systemAdminRepository
    ) {
        this.identitySetupService = identitySetupService;
        this.userRepository = userRepository;
        this.systemAdminRepository = systemAdminRepository;
    }

    @Transactional(readOnly = true)
    public InitialSetupStatusResponse status() {
        boolean completed = userRepository.count() > 0;
        return new InitialSetupStatusResponse(!completed, completed);
    }

    @Transactional
    public InitialSetupResponse createInitialSetup(InitialSetupRequest request) {
        InitialSetupRequest setupRequest = SetupRequestValidator.required(request, "request");
        if (userRepository.count() > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Initial setup has already been completed");
        }
        User adminUser = identitySetupService.createAdminUser(setupRequest.adminUser());
        grantSystemAdmin(adminUser);

        return new InitialSetupResponse(new InitialSetupResponse.UserSummary(
                adminUser.getId(),
                adminUser.getEmail(),
                adminUser.getUsername(),
                adminUser.getDisplayName(),
                adminUser.getAccountType()
        ));
    }

    private void grantSystemAdmin(User adminUser) {
        SystemAdmin systemAdmin = new SystemAdmin();
        systemAdmin.setUserId(adminUser.getId());
        systemAdmin.setActive(true);
        systemAdmin.setGrantedById(adminUser.getId());
        systemAdmin.setGrantedAt(OffsetDateTime.now());
        systemAdminRepository.save(systemAdmin);
    }
}
