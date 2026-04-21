package com.strangequark.trasck.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserInvitationRepository extends JpaRepository<UserInvitation, UUID> {
    Optional<UserInvitation> findByTokenHash(String tokenHash);

    Optional<UserInvitation> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<UserInvitation> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    List<UserInvitation> findByWorkspaceIdAndStatusIgnoreCaseOrderByCreatedAtDesc(UUID workspaceId, String status);
}
