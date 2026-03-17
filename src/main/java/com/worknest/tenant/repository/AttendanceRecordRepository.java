package com.worknest.tenant.repository;

import com.worknest.tenant.entity.AttendanceRecord;
import com.worknest.tenant.enums.AttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    Optional<AttendanceRecord> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);

    List<AttendanceRecord> findByEmployeeIdOrderByWorkDateDesc(Long employeeId);

    List<AttendanceRecord> findByWorkDateOrderByEmployeeIdAsc(LocalDate workDate);

    List<AttendanceRecord> findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(
            Long employeeId,
            LocalDate from,
            LocalDate to);

    Optional<AttendanceRecord> findFirstByEmployeeIdOrderByWorkDateDesc(Long employeeId);

    long countByWorkDate(LocalDate workDate);

    long countByWorkDateAndStatus(LocalDate workDate, AttendanceStatus status);

    @Query("""
            SELECT ar.workDate, ar.status, COUNT(ar)
            FROM AttendanceRecord ar
            WHERE ar.workDate BETWEEN :fromDate AND :toDate
            GROUP BY ar.workDate, ar.status
            ORDER BY ar.workDate ASC
            """)
    List<Object[]> findTrendCounts(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
}
