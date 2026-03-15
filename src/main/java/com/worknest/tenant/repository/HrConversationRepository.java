package com.worknest.tenant.repository;

import com.worknest.tenant.entity.HrConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HrConversationRepository extends JpaRepository<HrConversation, Long> {

    Optional<HrConversation> findByEmployeeIdAndHrId(Long employeeId, Long hrId);

    List<HrConversation> findByEmployeeIdOrHrIdOrderByUpdatedAtDesc(Long employeeId, Long hrId);

    @Query("""
            SELECT hc
            FROM HrConversation hc
            JOIN FETCH hc.employee
            JOIN FETCH hc.hr
            WHERE hc.id = :id
            """)
    Optional<HrConversation> findByIdWithParticipants(@Param("id") Long id);
}
