package com.worknest.tenant.repository;

import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.EmployeeSkill;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmployeeSkillRepository extends JpaRepository<EmployeeSkill, Long> {

    List<EmployeeSkill> findByEmployeeIdOrderBySkillNameAsc(Long employeeId);

    Optional<EmployeeSkill> findByIdAndEmployeeId(Long id, Long employeeId);

    boolean existsByEmployeeAndSkillNameIgnoreCase(Employee employee, String skillName);

    long countByEmployeeId(Long employeeId);

    @Query("""
            SELECT DISTINCT employeeSkill.skillName
            FROM EmployeeSkill employeeSkill
            WHERE :search = ''
               OR LOWER(employeeSkill.skillName) LIKE LOWER(CONCAT('%', :search, '%'))
            ORDER BY employeeSkill.skillName
            """)
    List<String> findDistinctSkillNames(@Param("search") String search, Pageable pageable);
}
