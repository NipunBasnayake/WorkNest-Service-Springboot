package com.worknest.tenant.repository;

import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.EmployeeSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeSkillRepository extends JpaRepository<EmployeeSkill, Long> {

    List<EmployeeSkill> findByEmployeeIdOrderBySkillNameAsc(Long employeeId);

    Optional<EmployeeSkill> findByIdAndEmployeeId(Long id, Long employeeId);

    boolean existsByEmployeeAndSkillNameIgnoreCase(Employee employee, String skillName);
}
