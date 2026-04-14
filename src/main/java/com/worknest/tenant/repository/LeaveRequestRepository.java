package com.worknest.tenant.repository;

import com.worknest.tenant.entity.LeaveRequest;
import com.worknest.tenant.enums.LeaveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    List<LeaveRequest> findByStatusOrderByCreatedAtAsc(LeaveStatus status);

    Page<LeaveRequest> findByStatusOrderByCreatedAtDesc(LeaveStatus status, Pageable pageable);

    long countByStatus(LeaveStatus status);

    long countByEmployeeIdAndStatus(Long employeeId, LeaveStatus status);

    Page<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId, Pageable pageable);

    @Query("""
            SELECT lr
            FROM LeaveRequest lr
            WHERE (:status IS NULL OR lr.status = :status)
              AND (:fromDate IS NULL OR lr.startDate >= :fromDate)
              AND (:toDate IS NULL OR lr.endDate <= :toDate)
            """)
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
    List<LeaveRequest> findUpcomingLeavesForReminder(
            @Param("status") LeaveStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);
}
