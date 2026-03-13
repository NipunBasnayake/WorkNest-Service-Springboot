package com.worknest.tenant.service;

import com.worknest.tenant.entity.Employee;

import java.util.List;

public interface EmployeeService {

    Employee createEmployee(Employee employee);

    Employee getEmployeeById(Long id);

    List<Employee> getAllEmployees();

    Employee updateEmployee(Long id, Employee employee);

    void deleteEmployee(Long id);

    List<Employee> getEmployeesByDepartment(String department);

    List<Employee> getEmployeesByStatus(String status);
}

