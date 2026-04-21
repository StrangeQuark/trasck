package com.strangequark.trasck.activity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    List<Attachment> findByIdInOrderByCreatedAtAsc(List<UUID> ids);

    @Query("""
            select a
            from Attachment a
            join WorkItemAttachment wia on wia.id.attachmentId = a.id
            where wia.id.workItemId = :workItemId
              and lower(coalesce(a.visibility, 'restricted')) = 'public'
            order by a.createdAt asc, a.id asc
            """)
    List<Attachment> findPublicByWorkItemIdOrderByCreatedAtAsc(@Param("workItemId") UUID workItemId);

    @Query("""
            select a
            from Attachment a
            join WorkItemAttachment wia on wia.id.attachmentId = a.id
            where wia.id.workItemId = :workItemId
              and a.id = :attachmentId
              and lower(coalesce(a.visibility, 'restricted')) = 'public'
            """)
    Optional<Attachment> findPublicByWorkItemIdAndId(
            @Param("workItemId") UUID workItemId,
            @Param("attachmentId") UUID attachmentId
    );
}
