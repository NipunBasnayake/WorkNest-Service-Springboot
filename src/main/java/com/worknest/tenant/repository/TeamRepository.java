package com.worknest.tenant.repository;

import com.worknest.tenant.entity.Team;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    boolean existsByNameIgnoreCase(String name);

    Optional<Team> findByNameIgnoreCase(String name);

    List<Team> findByManagerId(Long managerId);

    @Query("""
            SELECT t
            FROM Team t
            WHERE (:managerId IS NULL OR t.manager.id = :managerId)
              AND (
                    :search IS NULL OR :search = ''
                    OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
            """)
    Page<Team> search(
            @Param("managerId") Long managerId,
            @Param("search") String search,
            Pageable pageable);
}
