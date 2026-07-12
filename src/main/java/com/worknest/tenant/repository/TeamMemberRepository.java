package com.worknest.tenant.repository;

import com.worknest.common.enums.UserStatus;
import com.worknest.tenant.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    List<TeamMember> findByTeamIdOrderByJoinedAtDesc(Long teamId);

    List<TeamMember> findByTeamIdAndLeftAtIsNull(Long teamId);

    @Query("""
            SELECT tm
            FROM TeamMember tm
            JOIN FETCH tm.employee e
            WHERE tm.team.id = :teamId
              AND tm.leftAt IS NULL
              AND e.status = :status
            ORDER BY LOWER(e.firstName), LOWER(e.lastName), LOWER(e.email)
            """)
    List<TeamMember> findAssignableMembersByTeamId(
            @Param("teamId") Long teamId,
            @Param("status") UserStatus status);

    List<TeamMember> findByEmployeeIdAndLeftAtIsNull(Long employeeId);

    Optional<TeamMember> findFirstByIdAndTeamIdAndLeftAtIsNull(Long id, Long teamId);

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

    @Query("""
            SELECT tm.team.id, tm.team.name, COUNT(tm) FROM TeamMember tm
            WHERE tm.leftAt IS NULL AND (:teamId IS NULL OR tm.team.id = :teamId)
            GROUP BY tm.team.id, tm.team.name ORDER BY COUNT(tm) DESC
            """)
    List<Object[]> countActiveMembersForReport(@Param("teamId") Long teamId);

    void deleteByTeamId(Long teamId);
}
