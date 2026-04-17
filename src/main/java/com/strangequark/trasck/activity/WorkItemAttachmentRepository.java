package com.strangequark.trasck.activity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkItemAttachmentRepository extends JpaRepository<WorkItemAttachment, WorkItemAttachmentId> {
}
