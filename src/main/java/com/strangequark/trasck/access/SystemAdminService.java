package com.strangequark.trasck.access;

import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.identity.User;
import com.strangequark.trasck.identity.UserRepository;
import com.strangequark.trasck.config.RuntimeSecurityProfile;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SystemAdminService {

    private final SystemAdminRepository systemAdminRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final RuntimeSecurityProfile runtimeSecurityProfile;
    private final Duration stepUpWindow;

    public SystemAdminService(
            SystemAdminRepository systemAdminRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService,
            PermissionService permissionService,
            RuntimeSecurityProfile runtimeSecurityProfile,
            @Value("${trasck.security.system-admin.step-up-window:PT15M}") String stepUpWindow
    ) {
        this.systemAdminRepository = systemAdminRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
        this.runtimeSecurityProfile = runtimeSecurityProfile;
        this.stepUpWindow = Duration.parse(stepUpWindow);
    }

    @Transactional(readOnly = true)
    public List<SystemAdminResponse> listSystemAdmins() {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireSystemAdmin(actorId);
        List<SystemAdmin> admins = systemAdminRepository.findAll().stream()
                .sorted(Comparator.comparing(SystemAdmin::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Map<UUID, User> users = userRepository.findAllById(admins.stream().map(SystemAdmin::getUserId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        return admins.stream()
                .map(admin -> SystemAdminResponse.from(admin, users.get(admin.getUserId())))
                .toList();
    }

    @Transactional
    public SystemAdminResponse grantSystemAdmin(SystemAdminRequest request) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireSystemAdmin(actorId);
        requireRecentAuthentication(actorId);
        UUID userId = required(request == null ? null : request.userId(), "userId");
        User user = activeUser(userId);
        SystemAdmin admin = systemAdminRepository.findByUserId(userId).orElseGet(SystemAdmin::new);
        if (admin.getId() == null) {
            admin.setUserId(userId);
        }
        admin.setActive(true);
        admin.setGrantedById(actorId);
        admin.setGrantedAt(OffsetDateTime.now(ZoneOffset.UTC));
        admin.setRevokedAt(null);
        return SystemAdminResponse.from(systemAdminRepository.save(admin), user);
    }

    @Transactional
    public SystemAdminResponse revokeSystemAdmin(UUID userId) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireSystemAdmin(actorId);
        requireRecentAuthentication(actorId);
        UUID targetUserId = required(userId, "userId");
        SystemAdmin admin = systemAdminRepository.findByUserId(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "System admin not found"));
        if (Boolean.TRUE.equals(admin.getActive()) && systemAdminRepository.countByActiveTrue() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "At least one active system admin is required");
        }
        admin.setActive(false);
        admin.setRevokedAt(OffsetDateTime.now(ZoneOffset.UTC));
        User user = userRepository.findById(targetUserId).orElse(null);
        return SystemAdminResponse.from(systemAdminRepository.save(admin), user);
    }

    private void requireRecentAuthentication(UUID actorId) {
        if (!runtimeSecurityProfile.isProductionLike()) {
            return;
        }
        User actor = activeUser(actorId);
        OffsetDateTime lastLoginAt = actor.getLastLoginAt();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (lastLoginAt == null || lastLoginAt.plus(stepUpWindow).isBefore(now)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Recent authentication is required for system-admin changes");
        }
    }

    private User activeUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getDeletedAt() != null || !Boolean.TRUE.equals(user.getActive())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return user;
    }

    private UUID required(UUID value, String fieldName) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value;
    }
}
