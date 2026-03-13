package com.worknest.tenant.repository;

import com.worknest.tenant.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmail(String email);

    Optional<Employee> findByEmployeeCode(String employeeCode);

    List<Employee> findByDepartment(String department);

    List<Employee> findByStatus(String status);

    boolean existsByEmail(String email);

    boolean existsByEmployeeCode(String employeeCode);
}

