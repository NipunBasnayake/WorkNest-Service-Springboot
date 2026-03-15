package com.worknest.tenant.repository;

import com.worknest.tenant.entity.Attachment;
import com.worknest.tenant.enums.AttachmentEntityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(AttachmentEntityType entityType, Long entityId);
}
