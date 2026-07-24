package com.worknest.tenant.repository;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.tenant.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.time.LocalDate;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmailIgnoreCase(String email);

    Optional<Employee> findByPlatformUserId(Long platformUserId);

    Optional<Employee> findByEmployeeCodeIgnoreCase(String employeeCode);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmployeeCodeIgnoreCase(String employeeCode);

    List<Employee> findByStatus(UserStatus status);

    List<Employee> findByRoleInAndStatus(List<PlatformRole> roles, UserStatus status);

    long countByStatus(UserStatus status);

    @Query(value = """
            SELECT e.role, COUNT(e)
            FROM Employee e
            GROUP BY e.role
            """)
    List<Object[]> countByRole();

    @Query("""
            SELECT COALESCE(NULLIF(TRIM(e.designation), ''), 'UNSPECIFIED'), COUNT(e)
            FROM Employee e
            GROUP BY COALESCE(NULLIF(TRIM(e.designation), ''), 'UNSPECIFIED')
            ORDER BY COUNT(e) DESC
            """)
    List<Object[]> countByDesignation();

    @Query("""
            SELECT COALESCE(NULLIF(TRIM(e.department), ''), 'Unassigned'), COUNT(e)
            FROM Employee e GROUP BY COALESCE(NULLIF(TRIM(e.department), ''), 'Unassigned')
            ORDER BY COUNT(e) DESC
            """)
    List<Object[]> countByDepartment();

    @Query("SELECT e.status, COUNT(e) FROM Employee e WHERE (:department IS NULL OR e.department = :department) GROUP BY e.status")
    List<Object[]> countByStatusGroup(@Param("department") String department);

    @Query("""
            SELECT e.status, COUNT(e) FROM Employee e
            WHERE e.joinedDate BETWEEN :fromDate AND :toDate
              AND (:department IS NULL OR e.department = :department)
              AND (:employeeId IS NULL OR e.id = :employeeId)
              AND (:teamId IS NULL OR EXISTS (SELECT tm.id FROM TeamMember tm WHERE tm.team.id = :teamId AND tm.employee.id = e.id AND tm.leftAt IS NULL))
              AND (:projectId IS NULL OR EXISTS (SELECT task.id FROM Task task WHERE task.project.id = :projectId AND task.assignee.id = e.id))
            GROUP BY e.status
            """)
    List<Object[]> countStatusForReport(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("department") String department,
            @Param("employeeId") Long employeeId, @Param("teamId") Long teamId, @Param("projectId") Long projectId);

    @Query("""
            SELECT COALESCE(NULLIF(TRIM(e.department), ''), 'Unassigned'), COUNT(e)
            FROM Employee e
            WHERE e.joinedDate BETWEEN :fromDate AND :toDate
              AND (:department IS NULL OR e.department = :department)
              AND (:employeeId IS NULL OR e.id = :employeeId)
              AND (:teamId IS NULL OR EXISTS (SELECT tm.id FROM TeamMember tm WHERE tm.team.id = :teamId AND tm.employee.id = e.id AND tm.leftAt IS NULL))
              AND (:projectId IS NULL OR EXISTS (SELECT task.id FROM Task task WHERE task.project.id = :projectId AND task.assignee.id = e.id))
            GROUP BY COALESCE(NULLIF(TRIM(e.department), ''), 'Unassigned')
            ORDER BY COUNT(e) DESC
            """)
    List<Object[]> countDepartmentForReport(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("department") String department,
            @Param("employeeId") Long employeeId, @Param("teamId") Long teamId, @Param("projectId") Long projectId);

    @Query("""
            SELECT COALESCE(NULLIF(TRIM(e.designation), ''), 'Unspecified'), COUNT(e)
            FROM Employee e
            WHERE e.joinedDate BETWEEN :fromDate AND :toDate
              AND (:department IS NULL OR e.department = :department)
              AND (:employeeId IS NULL OR e.id = :employeeId)
              AND (:teamId IS NULL OR EXISTS (SELECT tm.id FROM TeamMember tm WHERE tm.team.id = :teamId AND tm.employee.id = e.id AND tm.leftAt IS NULL))
              AND (:projectId IS NULL OR EXISTS (SELECT task.id FROM Task task WHERE task.project.id = :projectId AND task.assignee.id = e.id))
            GROUP BY COALESCE(NULLIF(TRIM(e.designation), ''), 'Unspecified')
            ORDER BY COUNT(e) DESC
            """)
    List<Object[]> countDesignationForReport(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("department") String department,
            @Param("employeeId") Long employeeId, @Param("teamId") Long teamId, @Param("projectId") Long projectId);

    @Query("""
            SELECT e.role, COUNT(e) FROM Employee e
            WHERE e.joinedDate BETWEEN :fromDate AND :toDate
              AND (:department IS NULL OR e.department = :department)
              AND (:employeeId IS NULL OR e.id = :employeeId)
              AND (:teamId IS NULL OR EXISTS (SELECT tm.id FROM TeamMember tm WHERE tm.team.id = :teamId AND tm.employee.id = e.id AND tm.leftAt IS NULL))
              AND (:projectId IS NULL OR EXISTS (SELECT task.id FROM Task task WHERE task.project.id = :projectId AND task.assignee.id = e.id))
            GROUP BY e.role
            """)
    List<Object[]> countRoleForReport(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("department") String department,
            @Param("employeeId") Long employeeId, @Param("teamId") Long teamId, @Param("projectId") Long projectId);

    @Query(value = """
            SELECT DATE_FORMAT(e.joined_date, '%Y-%m'), COUNT(*) FROM employees e
            WHERE e.joined_date BETWEEN :fromDate AND :toDate
              AND (:department IS NULL OR e.department = :department)
            GROUP BY DATE_FORMAT(e.joined_date, '%Y-%m') ORDER BY DATE_FORMAT(e.joined_date, '%Y-%m')
            """, nativeQuery = true)
    List<Object[]> countJoiningTrend(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
            @Param("department") String department);

    @Query(value = """
            SELECT DATE_FORMAT(e.joined_date, '%Y-%m'), COUNT(*) FROM employees e
            WHERE e.joined_date BETWEEN :fromDate AND :toDate
              AND (:department IS NULL OR e.department = :department)
              AND (:employeeId IS NULL OR e.id = :employeeId)
              AND (:teamId IS NULL OR EXISTS (SELECT 1 FROM team_members tm WHERE tm.team_id = :teamId AND tm.employee_id = e.id AND tm.left_at IS NULL))
              AND (:projectId IS NULL OR EXISTS (SELECT 1 FROM tasks t WHERE t.project_id = :projectId AND t.assignee_id = e.id))
            GROUP BY DATE_FORMAT(e.joined_date, '%Y-%m') ORDER BY DATE_FORMAT(e.joined_date, '%Y-%m')
            """, nativeQuery = true)
    List<Object[]> countJoiningTrendForReport(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("department") String department,
            @Param("employeeId") Long employeeId, @Param("teamId") Long teamId, @Param("projectId") Long projectId);

    @Query("SELECT DISTINCT e.department FROM Employee e WHERE e.department IS NOT NULL AND TRIM(e.department) <> '' ORDER BY e.department")
    List<String> findDistinctDepartments();

    @Query("SELECT e.id, CONCAT(e.firstName, ' ', e.lastName) FROM Employee e ORDER BY e.firstName, e.lastName")
    List<Object[]> findReportOptions();

    @EntityGraph(attributePaths = "avatarAsset")
    @Query(value = """
            SELECT DISTINCT e
            FROM Employee e
            LEFT JOIN e.skills employeeSkill
            WHERE (:role IS NULL OR e.role = :role)
              AND (:status IS NULL OR e.status = :status)
              AND (
                    :search IS NULL OR :search = ''
                    OR LOWER(e.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(e.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(e.email) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(e.designation, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(e.department, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(e.phone, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(employeeSkill.skillName, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
            """, countQuery = """
            SELECT COUNT(DISTINCT e)
            FROM Employee e
            LEFT JOIN e.skills employeeSkill
            WHERE (:role IS NULL OR e.role = :role)
              AND (:status IS NULL OR e.status = :status)
              AND (
                    :search IS NULL OR :search = ''
                    OR LOWER(e.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(e.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(e.email) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(e.designation, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(e.department, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(e.phone, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(employeeSkill.skillName, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
            """)
    Page<Employee> search(
            @Param("role") PlatformRole role,
            @Param("status") UserStatus status,
            @Param("search") String search,
            Pageable pageable);
}
