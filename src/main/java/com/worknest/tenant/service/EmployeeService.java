package com.worknest.tenant.service;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.dto.employee.*;

import java.util.List;

public interface EmployeeService {

    EmployeeResponseDto createEmployee(EmployeeCreateRequestDto requestDto);

    EmployeeResponseDto updateEmployee(Long employeeId, EmployeeUpdateRequestDto requestDto);

    EmployeeResponseDto updateEmployeeStatus(Long employeeId, EmployeeStatusUpdateDto requestDto);

    EmployeeResponseDto getEmployeeById(Long employeeId);

    PagedResultDto<EmployeeResponseDto> listEmployees(
            PlatformRole role,
            UserStatus status,
            String search,
            int page,
            int size,
            String sortBy,
            String sortDir);

    EmployeeResponseDto getMyProfile();

    EmployeeResponseDto updateMyProfile(EmployeeSelfUpdateDto requestDto);

    EmployeeAccountProvisionResponseDto provisionEmployeeAccount(
            Long employeeId,
            EmployeeAccountProvisionRequestDto requestDto);

    EmployeeSkillResponseDto addSkill(Long employeeId, EmployeeSkillCreateRequestDto requestDto);

    EmployeeSkillResponseDto updateSkill(Long employeeId, Long skillId, EmployeeSkillCreateRequestDto requestDto);

    void deleteSkill(Long employeeId, Long skillId);

    List<EmployeeSkillResponseDto> listSkillsByEmployee(Long employeeId);

    List<EmployeeSkillResponseDto> listMySkills();
}
