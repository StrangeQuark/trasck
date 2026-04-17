package com.strangequark.trasck.setup;

import com.strangequark.trasck.identity.User;
import com.strangequark.trasck.identity.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IdentitySetupService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public IdentitySetupService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    User createAdminUser(InitialSetupRequest.AdminUserRequest request) {
        InitialSetupRequest.AdminUserRequest admin = SetupRequestValidator.required(request, "adminUser");
        String email = SetupRequestValidator.requiredText(admin.email(), "adminUser.email").toLowerCase();
        String username = SetupRequestValidator.requiredText(admin.username(), "adminUser.username");
        String displayName = SetupRequestValidator.requiredText(admin.displayName(), "adminUser.displayName");
        String password = SetupRequestValidator.requiredText(admin.password(), "adminUser.password");

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A user with this email already exists");
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A user with this username already exists");
        }

        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setAccountType("human");
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEmailVerified(false);
        user.setActive(true);
        return userRepository.save(user);
    }
}
