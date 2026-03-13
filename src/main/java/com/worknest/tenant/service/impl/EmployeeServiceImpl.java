package com.worknest.tenant.service.impl;

import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.common.util.EmployeeCodeGenerator;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.repository.EmployeeRepository;
import com.worknest.tenant.service.EmployeeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;

    public EmployeeServiceImpl(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @Override
    public Employee createEmployee(Employee employee) {
        // Validate email uniqueness
        if (employeeRepository.existsByEmail(employee.getEmail())) {
            throw new BadRequestException(
                    "Employee with email " + employee.getEmail() + " already exists");
        }

        // Generate employee code if not provided
        if (employee.getEmployeeCode() == null || employee.getEmployeeCode().trim().isEmpty()) {
            employee.setEmployeeCode(generateUniqueEmployeeCode());
        } else {
            // Validate uniqueness if code is provided
            if (employeeRepository.existsByEmployeeCode(employee.getEmployeeCode())) {
                throw new BadRequestException(
                        "Employee with code " + employee.getEmployeeCode() + " already exists");
            }
        }

        // Set default values
        if (employee.getStatus() == null) {
            employee.setStatus("ACTIVE");
        }
        if (employee.getHireDate() == null) {
            employee.setHireDate(LocalDateTime.now());
        }

        return employeeRepository.save(employee);
    }

    @Override
    @Transactional(readOnly = true)
    public Employee getEmployeeById(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
    }

    @Override
    public Employee updateEmployee(Long id, Employee employee) {
        Employee existingEmployee = getEmployeeById(id);

        // Update fields
        existingEmployee.setFirstName(employee.getFirstName());
        existingEmployee.setLastName(employee.getLastName());

        // Check email uniqueness if changed
        if (!existingEmployee.getEmail().equals(employee.getEmail())) {
            if (employeeRepository.existsByEmail(employee.getEmail())) {
                throw new BadRequestException(
                        "Employee with email " + employee.getEmail() + " already exists");
            }
            existingEmployee.setEmail(employee.getEmail());
        }

        existingEmployee.setPhone(employee.getPhone());
        existingEmployee.setPosition(employee.getPosition());
        existingEmployee.setDepartment(employee.getDepartment());
        existingEmployee.setSalary(employee.getSalary());
        existingEmployee.setStatus(employee.getStatus());

        return employeeRepository.save(existingEmployee);
    }

    @Override
    public void deleteEmployee(Long id) {
        Employee employee = getEmployeeById(id);
        employeeRepository.delete(employee);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Employee> getEmployeesByDepartment(String department) {
        return employeeRepository.findByDepartment(department);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Employee> getEmployeesByStatus(String status) {
        return employeeRepository.findByStatus(status);
    }

    /**
     * Generate a unique employee code.
     * Keeps trying until a unique code is found.
     */
    private String generateUniqueEmployeeCode() {
        String code;
        int attempts = 0;
        do {
            code = EmployeeCodeGenerator.generateCode();
            attempts++;
            if (attempts > 100) {
                throw new BadRequestException(
                        "Unable to generate unique employee code after 100 attempts");
            }
        } while (employeeRepository.existsByEmployeeCode(code));

        return code;
    }
}

