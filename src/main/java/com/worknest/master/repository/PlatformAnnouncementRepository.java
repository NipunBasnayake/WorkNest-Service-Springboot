package com.worknest.master.repository;

import com.worknest.master.entity.PlatformAnnouncement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlatformAnnouncementRepository extends JpaRepository<PlatformAnnouncement, Long> {

    @Override
    @EntityGraph(attributePaths = {"createdBy"})
    List<PlatformAnnouncement> findAll();
}
