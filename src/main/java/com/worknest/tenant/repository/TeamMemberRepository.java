package com.worknest.tenant.repository;

import com.worknest.tenant.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    List<TeamMember> findByTeamIdOrderByJoinedAtDesc(Long teamId);

    List<TeamMember> findByTeamIdAndLeftAtIsNull(Long teamId);

    List<TeamMember> findByEmployeeIdAndLeftAtIsNull(Long employeeId);

    Optional<TeamMember> findFirstByTeamIdAndEmployeeIdAndLeftAtIsNull(Long teamId, Long employeeId);

    long countByTeamIdAndLeftAtIsNull(Long teamId);

    @Query("""
            SELECT tm.team.id, COUNT(tm)
            FROM TeamMember tm
            WHERE tm.leftAt IS NULL
              AND tm.team.id IN :teamIds
            GROUP BY tm.team.id
            """)
    List<Object[]> countActiveMembersByTeamIds(@Param("teamIds") List<Long> teamIds);

    void deleteByTeamId(Long teamId);
}
