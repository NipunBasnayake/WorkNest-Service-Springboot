package com.worknest.tenant.repository;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.tenant.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmailIgnoreCase(String email);

    Optional<Employee> findByPlatformUserId(Long platformUserId);

    Optional<Employee> findByEmployeeCodeIgnoreCase(String employeeCode);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmployeeCodeIgnoreCase(String employeeCode);

    List<Employee> findByStatus(UserStatus status);

    List<Employee> findByRoleInAndStatus(List<PlatformRole> roles, UserStatus status);

    long countByStatus(UserStatus status);

    @Query("""
            SELECT e.role, COUNT(e)
            FROM Employee e
            GROUP BY e.role
            """)
    List<Object[]> countByRole();

    @Query("""
            SELECT COALESCE(NULLIF(TRIM(e.designation), ''), 'UNSPECIFIED'), COUNT(e)
            FROM Employee e
            GROUP BY COALESCE(NULLIF(TRIM(e.designation), ''), 'UNSPECIFIED')
            ORDER BY COUNT(e) DESC
            """)
    List<Object[]> countByDesignation();

    @Query("""
            SELECT e
            FROM Employee e
            WHERE (:role IS NULL OR e.role = :role)
              AND (:status IS NULL OR e.status = :status)
              AND (
                    :search IS NULL OR :search = ''
                    OR LOWER(e.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(e.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(e.email) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(e.designation, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(e.department, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(e.phone, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
            """)
    Page<Employee> search(
            @Param("role") PlatformRole role,
            @Param("status") UserStatus status,
            @Param("search") String search,
            Pageable pageable);
}
