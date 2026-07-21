package com.worknest.tenant.repository;

import com.worknest.tenant.entity.LeaveRequest;
import com.worknest.tenant.enums.LeaveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    @EntityGraph(attributePaths = {"employee", "approver"})
    List<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    @EntityGraph(attributePaths = {"employee", "approver"})
    List<LeaveRequest> findByStatusOrderByCreatedAtAsc(LeaveStatus status);

    @EntityGraph(attributePaths = {"employee", "approver"})
    Page<LeaveRequest> findByStatusOrderByCreatedAtDesc(LeaveStatus status, Pageable pageable);

    long countByStatus(LeaveStatus status);

    long countByEmployeeIdAndStatus(Long employeeId, LeaveStatus status);

    @EntityGraph(attributePaths = {"employee", "approver"})
    Page<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId, Pageable pageable);

    @Query("""
            SELECT lr
            FROM LeaveRequest lr
            WHERE (:status IS NULL OR lr.status = :status)
              AND (:fromDate IS NULL OR lr.startDate >= :fromDate)
              AND (:toDate IS NULL OR lr.endDate <= :toDate)
            """)
    @EntityGraph(attributePaths = {"employee", "approver"})
    Page<LeaveRequest> search(
            @Param("status") LeaveStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable);

    @Query("""
            SELECT lr
            FROM LeaveRequest lr
            WHERE lr.employee.id = :employeeId
              AND (:status IS NULL OR lr.status = :status)
              AND (:fromDate IS NULL OR lr.startDate >= :fromDate)
              AND (:toDate IS NULL OR lr.endDate <= :toDate)
            """)
    @EntityGraph(attributePaths = {"employee", "approver"})
    Page<LeaveRequest> searchMyRequests(
            @Param("employeeId") Long employeeId,
            @Param("status") LeaveStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable);

    @Query("""
            SELECT lr.status, COUNT(lr)
            FROM LeaveRequest lr
            GROUP BY lr.status
            """)
    List<Object[]> countByStatusGroup();

    @Query("""
            SELECT lr.status, COUNT(lr) FROM LeaveRequest lr
            WHERE lr.startDate <= :toDate AND lr.endDate >= :fromDate
              AND (:employeeId IS NULL OR lr.employee.id = :employeeId)
              AND (:department IS NULL OR lr.employee.department = :department)
              AND (:leaveType IS NULL OR lr.leaveType = :leaveType)
            GROUP BY lr.status
            """)
    List<Object[]> countStatusForReport(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
            @Param("employeeId") Long employeeId, @Param("department") String department,
            @Param("leaveType") com.worknest.tenant.enums.LeaveType leaveType);

    @Query("""
            SELECT lr.leaveType, COUNT(lr) FROM LeaveRequest lr
            WHERE lr.startDate <= :toDate AND lr.endDate >= :fromDate
              AND (:employeeId IS NULL OR lr.employee.id = :employeeId)
              AND (:department IS NULL OR lr.employee.department = :department)
            GROUP BY lr.leaveType
            """)
    List<Object[]> countTypeForReport(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
            @Param("employeeId") Long employeeId, @Param("department") String department);

    @Query(value = """
            SELECT DATE_FORMAT(lr.start_date, '%Y-%m'), COUNT(*),
                   SUM(CASE WHEN lr.status = 'APPROVED' THEN 1 ELSE 0 END)
            FROM leave_requests lr JOIN employees e ON e.id = lr.employee_id
            WHERE lr.start_date BETWEEN :fromDate AND :toDate
              AND (:employeeId IS NULL OR lr.employee_id = :employeeId)
              AND (:department IS NULL OR e.department = :department)
            GROUP BY DATE_FORMAT(lr.start_date, '%Y-%m') ORDER BY DATE_FORMAT(lr.start_date, '%Y-%m')
            """, nativeQuery = true)
    List<Object[]> countTrendForReport(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
            @Param("employeeId") Long employeeId, @Param("department") String department);

    @Query(value = """
            SELECT COALESCE(AVG(DATEDIFF(lr.end_date, lr.start_date) + 1), 0)
            FROM leave_requests lr JOIN employees e ON e.id = lr.employee_id
            WHERE lr.start_date <= :toDate AND lr.end_date >= :fromDate
              AND (:employeeId IS NULL OR lr.employee_id = :employeeId)
              AND (:department IS NULL OR e.department = :department)
            """, nativeQuery = true)
    double averageLeaveDaysForReport(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
            @Param("employeeId") Long employeeId, @Param("department") String department);

    @Query("""
            SELECT lr.status, COUNT(lr)
            FROM LeaveRequest lr
            WHERE lr.employee.id = :employeeId
            GROUP BY lr.status
            """)
    List<Object[]> countByStatusGroupForEmployee(@Param("employeeId") Long employeeId);

    @Query(
            value = """
                    SELECT DATE_FORMAT(lr.start_date, '%Y-%m') AS month_key, lr.status, COUNT(*) AS total_count
                    FROM leave_requests lr
                    WHERE YEAR(lr.start_date) = :year
                    GROUP BY DATE_FORMAT(lr.start_date, '%Y-%m'), lr.status
                    ORDER BY month_key ASC
                    """,
            nativeQuery = true
    )
    List<Object[]> findLeaveTrendByYear(@Param("year") int year);

    boolean existsByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long employeeId,
            LeaveStatus status,
            LocalDate endDate,
            LocalDate startDate);

    @Query("""
            SELECT CASE WHEN COUNT(lr) > 0 THEN true ELSE false END
            FROM LeaveRequest lr
            WHERE lr.employee.id = :employeeId
              AND lr.status IN :statuses
              AND (:excludedId IS NULL OR lr.id <> :excludedId)
              AND lr.startDate <= :endDate
              AND lr.endDate >= :startDate
            """)
    boolean existsOverlappingLeave(
            @Param("employeeId") Long employeeId,
            @Param("statuses") Collection<LeaveStatus> statuses,
            @Param("excludedId") Long excludedId,
            @Param("endDate") LocalDate endDate,
            @Param("startDate") LocalDate startDate);

    @Query("""
            SELECT lr
            FROM LeaveRequest lr
            WHERE lr.status = :status
              AND lr.startDate BETWEEN :fromDate AND :toDate
              AND (lr.lastReminderSentForDate IS NULL OR lr.lastReminderSentForDate <> lr.startDate)
            ORDER BY lr.startDate ASC, lr.createdAt ASC
            """)
    @EntityGraph(attributePaths = "employee")
    List<LeaveRequest> findUpcomingLeavesForReminder(
            @Param("status") LeaveStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);
}
