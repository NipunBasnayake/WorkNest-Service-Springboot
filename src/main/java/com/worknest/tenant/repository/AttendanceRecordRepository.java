package com.worknest.tenant.repository;

import com.worknest.tenant.entity.AttendanceRecord;
import com.worknest.tenant.enums.AttendanceStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    Optional<AttendanceRecord> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);

    @EntityGraph(attributePaths = {"employee", "markedByEmployee"})
    List<AttendanceRecord> findByEmployeeIdOrderByWorkDateDesc(Long employeeId);

    @EntityGraph(attributePaths = {"employee", "markedByEmployee"})
    List<AttendanceRecord> findByWorkDateOrderByEmployeeIdAsc(LocalDate workDate);

        List<AttendanceRecord> findByWorkDateBetweenOrderByWorkDateAsc(LocalDate from, LocalDate to);

    List<AttendanceRecord> findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(
            Long employeeId,
            LocalDate from,
            LocalDate to);

    Optional<AttendanceRecord> findFirstByEmployeeIdOrderByWorkDateDesc(Long employeeId);

    long countByWorkDate(LocalDate workDate);

    long countByWorkDateAndStatus(LocalDate workDate, AttendanceStatus status);

        long countByWorkDateAndLateTrue(LocalDate workDate);

    @Query("""
            SELECT ar.workDate, ar.status, COUNT(ar)
            FROM AttendanceRecord ar
            WHERE ar.workDate BETWEEN :fromDate AND :toDate
            GROUP BY ar.workDate, ar.status
            ORDER BY ar.workDate ASC
            """)
    List<Object[]> findTrendCounts(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    @Query("""
            SELECT ar.workDate,
                   SUM(CASE WHEN ar.status = com.worknest.tenant.enums.AttendanceStatus.PRESENT THEN 1 ELSE 0 END),
                   SUM(CASE WHEN ar.late = true THEN 1 ELSE 0 END),
                   SUM(CASE WHEN ar.status = com.worknest.tenant.enums.AttendanceStatus.ABSENT THEN 1 ELSE 0 END)
            FROM AttendanceRecord ar
            WHERE ar.workDate BETWEEN :fromDate AND :toDate
              AND (:employeeId IS NULL OR ar.employee.id = :employeeId)
              AND (:department IS NULL OR ar.employee.department = :department)
            GROUP BY ar.workDate ORDER BY ar.workDate
            """)
    List<Object[]> summarizeForReport(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
            @Param("employeeId") Long employeeId, @Param("department") String department);

    @Query(value = """
            SELECT DATE_FORMAT(ar.work_date, '%x-W%v'),
                   SUM(CASE WHEN ar.status = 'PRESENT' THEN 1 ELSE 0 END),
                   SUM(CASE WHEN ar.late = true THEN 1 ELSE 0 END),
                   SUM(CASE WHEN ar.status = 'ABSENT' THEN 1 ELSE 0 END)
            FROM attendance_records ar JOIN employees e ON e.id = ar.employee_id
            WHERE ar.work_date BETWEEN :fromDate AND :toDate
              AND (:employeeId IS NULL OR ar.employee_id = :employeeId)
              AND (:department IS NULL OR e.department = :department)
            GROUP BY DATE_FORMAT(ar.work_date, '%x-W%v') ORDER BY DATE_FORMAT(ar.work_date, '%x-W%v')
            """, nativeQuery = true)
    List<Object[]> summarizeWeeklyForReport(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
            @Param("employeeId") Long employeeId, @Param("department") String department);

    @Query(value = """
            SELECT DATE_FORMAT(ar.work_date, '%Y-%m'),
                   SUM(CASE WHEN ar.status = 'PRESENT' THEN 1 ELSE 0 END),
                   SUM(CASE WHEN ar.late = true THEN 1 ELSE 0 END),
                   SUM(CASE WHEN ar.status = 'ABSENT' THEN 1 ELSE 0 END)
            FROM attendance_records ar JOIN employees e ON e.id = ar.employee_id
            WHERE ar.work_date BETWEEN :fromDate AND :toDate
              AND (:employeeId IS NULL OR ar.employee_id = :employeeId)
              AND (:department IS NULL OR e.department = :department)
            GROUP BY DATE_FORMAT(ar.work_date, '%Y-%m') ORDER BY DATE_FORMAT(ar.work_date, '%Y-%m')
            """, nativeQuery = true)
    List<Object[]> summarizeMonthlyForReport(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
            @Param("employeeId") Long employeeId, @Param("department") String department);
}
