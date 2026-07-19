package com.worknest.tenant.repository;

import com.worknest.tenant.entity.StoredFileVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoredFileVariantRepository extends JpaRepository<StoredFileVariant, Long> {
    List<StoredFileVariant> findBySourceFileIdOrderByWidthAsc(Long sourceFileId);
    Optional<StoredFileVariant> findBySourceFileIdAndVariantName(Long sourceFileId, String variantName);
}
