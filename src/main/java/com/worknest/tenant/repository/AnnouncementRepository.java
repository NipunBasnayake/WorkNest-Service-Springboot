package com.worknest.tenant.repository;

import com.worknest.tenant.entity.Announcement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    List<Announcement> findAllByOrderByCreatedAtDesc();

    List<Announcement> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("""
            SELECT a
            FROM Announcement a
            WHERE (
                    :search IS NULL OR :search = ''
                    OR LOWER(a.title) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(a.message) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
            """)
    Page<Announcement> search(@Param("search") String search, Pageable pageable);
}
