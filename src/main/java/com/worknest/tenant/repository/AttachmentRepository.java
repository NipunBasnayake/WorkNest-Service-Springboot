package com.worknest.tenant.repository;

import com.worknest.tenant.entity.Attachment;
import com.worknest.tenant.enums.AttachmentEntityType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    @EntityGraph(attributePaths = "uploadedBy")
    List<Attachment> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(AttachmentEntityType entityType, Long entityId);

    @EntityGraph(attributePaths = "uploadedBy")
    List<Attachment> findByEntityTypeAndEntityIdInOrderByCreatedAtDesc(
            AttachmentEntityType entityType,
            Collection<Long> entityIds);

    boolean existsByEntityTypeAndEntityId(AttachmentEntityType entityType, Long entityId);
}
