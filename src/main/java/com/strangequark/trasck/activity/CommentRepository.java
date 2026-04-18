package com.strangequark.trasck.activity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
    List<Comment> findByWorkItemIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID workItemId);

    Optional<Comment> findByIdAndWorkItemIdAndDeletedAtIsNull(UUID id, UUID workItemId);
}
