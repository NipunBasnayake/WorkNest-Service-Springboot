package com.worknest.tenant.repository;

import com.worknest.common.enums.UserStatus;
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
            SELECT COUNT(t)
            FROM Team t
            WHERE (t.manager IS NOT NULL AND t.manager.status = :activeStatus)
               OR EXISTS (
                    SELECT tm.id
                    FROM TeamMember tm
                    WHERE tm.team.id = t.id
                      AND tm.leftAt IS NULL
                      AND tm.employee.status = :activeStatus
               )
            """)
    long countActiveTeams(@Param("activeStatus") UserStatus activeStatus);

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
