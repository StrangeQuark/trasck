package com.strangequark.trasck.activity;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkItemAttachmentRepository extends JpaRepository<WorkItemAttachment, WorkItemAttachmentId> {
    List<WorkItemAttachment> findByIdWorkItemId(UUID workItemId);

    long countByIdAttachmentId(UUID attachmentId);
}
