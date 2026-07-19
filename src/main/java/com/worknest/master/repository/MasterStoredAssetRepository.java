package com.worknest.master.repository;

import com.worknest.master.entity.MasterStoredAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MasterStoredAssetRepository extends JpaRepository<MasterStoredAsset, Long> {
    Optional<MasterStoredAsset> findByPublicId(String publicId);
}
