package com.worknest.tenant.repository;

import com.worknest.tenant.entity.StoredFileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.List;

public interface StoredFileMetadataRepository extends JpaRepository<StoredFileMetadata, Long> {
    Optional<StoredFileMetadata> findByRelativePathAndActiveTrue(String relativePath);
    List<StoredFileMetadata> findByRelatedModuleAndRelatedEntityIdAndActiveTrueOrderByUploadedAtAsc(
            String relatedModule,
            Long relatedEntityId);

    List<StoredFileMetadata> findByRelatedModuleAndRelatedEntityIdInAndActiveTrueOrderByRelatedEntityIdAscUploadedAtAsc(
            String relatedModule,
            Collection<Long> relatedEntityIds);
}
