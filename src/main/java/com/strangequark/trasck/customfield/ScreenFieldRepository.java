package com.strangequark.trasck.customfield;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreenFieldRepository extends JpaRepository<ScreenField, UUID> {
    List<ScreenField> findByScreenIdOrderByPositionAsc(UUID screenId);

    Optional<ScreenField> findByIdAndScreenId(UUID id, UUID screenId);
}
