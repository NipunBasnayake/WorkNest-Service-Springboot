package com.worknest.tenant.repository;

import com.worknest.common.enums.UserStatus;
import com.worknest.tenant.entity.TeamMember;
import com.worknest.tenant.enums.TeamFunctionalRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    @EntityGraph(attributePaths = {"team", "employee"})
    List<TeamMember> findByTeamIdOrderByJoinedAtDesc(Long teamId);

    @EntityGraph(attributePaths = {"team", "employee"})
    List<TeamMember> findByTeamIdAndLeftAtIsNull(Long teamId);

    @EntityGraph(attributePaths = {"team", "employee"})
    List<TeamMember> findByTeamIdInAndLeftAtIsNull(Collection<Long> teamIds);

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

    @EntityGraph(attributePaths = {"team", "employee"})
    List<TeamMember> findByEmployeeIdAndLeftAtIsNull(Long employeeId);

    Optional<TeamMember> findFirstByIdAndTeamIdAndLeftAtIsNull(Long id, Long teamId);

    Optional<TeamMember> findFirstByTeamIdAndEmployeeIdAndLeftAtIsNull(Long teamId, Long employeeId);

    @Query("""
            SELECT CASE WHEN COUNT(tm) > 0 THEN true ELSE false END
            FROM TeamMember tm
            WHERE tm.employee.id = :employeeId
              AND tm.leftAt IS NULL
              AND tm.functionalRole IN :roles
              AND (
                NOT EXISTS (
                  SELECT unlinkedProjectTeam.id
                  FROM ProjectTeam unlinkedProjectTeam
                  WHERE unlinkedProjectTeam.project.id = :projectId
                )
                OR tm.team.id IN (
                  SELECT linkedProjectTeam.team.id
                  FROM ProjectTeam linkedProjectTeam
                  WHERE linkedProjectTeam.project.id = :projectId
                )
              )
            """)
    boolean existsActiveProjectRole(
            @Param("projectId") Long projectId,
            @Param("employeeId") Long employeeId,
            @Param("roles") Set<TeamFunctionalRole> roles);

    @Query("""
            SELECT CASE WHEN COUNT(tm) > 0 THEN true ELSE false END
            FROM TeamMember tm
            WHERE tm.employee.id = :employeeId
              AND tm.leftAt IS NULL
              AND tm.functionalRole IN :roles
              AND tm.team.id IN (
                SELECT linkedProjectTeam.team.id
                FROM ProjectTeam linkedProjectTeam
                WHERE linkedProjectTeam.project.id = :projectId
              )
            """)
    boolean existsActiveLinkedProjectRole(
            @Param("projectId") Long projectId,
            @Param("employeeId") Long employeeId,
            @Param("roles") Set<TeamFunctionalRole> roles);

    @Query("""
            SELECT CASE WHEN COUNT(tm) > 0 THEN true ELSE false END
            FROM TeamMember tm
            WHERE tm.employee.id = :employeeId
              AND tm.leftAt IS NULL
              AND tm.functionalRole = :role
              AND tm.team.id IN (
                SELECT linkedProjectTeam.team.id
                FROM ProjectTeam linkedProjectTeam
                WHERE linkedProjectTeam.project.id IN (
                  SELECT teamProject.project.id
                  FROM ProjectTeam teamProject
                  WHERE teamProject.team.id = :teamId
                )
              )
            """)
    boolean existsActiveRoleForProjectsLinkedToTeam(
            @Param("teamId") Long teamId,
            @Param("employeeId") Long employeeId,
            @Param("role") TeamFunctionalRole role);

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
