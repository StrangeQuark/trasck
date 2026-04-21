package com.strangequark.trasck.activity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
    List<Comment> findByWorkItemIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID workItemId);

    Optional<Comment> findByIdAndWorkItemIdAndDeletedAtIsNull(UUID id, UUID workItemId);

    @Query("""
            select c
            from Comment c
            where c.workItemId = :workItemId
              and c.deletedAt is null
              and lower(coalesce(c.visibility, 'workspace')) <> 'private'
            order by c.createdAt asc, c.id asc
            """)
    List<Comment> findPublicByWorkItemIdOrderByCreatedAtAsc(@Param("workItemId") UUID workItemId);
}
