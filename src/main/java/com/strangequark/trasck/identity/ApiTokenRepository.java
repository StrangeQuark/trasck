package com.strangequark.trasck.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiTokenRepository extends JpaRepository<ApiToken, UUID> {
    Optional<ApiToken> findByTokenHashAndRevokedAtIsNull(String tokenHash);

    Optional<ApiToken> findByIdAndUserIdAndTokenTypeAndRevokedAtIsNull(UUID id, UUID userId, String tokenType);

    Optional<ApiToken> findByIdAndWorkspaceIdAndTokenTypeAndRevokedAtIsNull(UUID id, UUID workspaceId, String tokenType);
}
