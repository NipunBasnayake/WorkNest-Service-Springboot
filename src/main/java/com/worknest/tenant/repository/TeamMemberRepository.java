package com.worknest.tenant.repository;

import com.worknest.tenant.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    List<TeamMember> findByTeamIdOrderByJoinedAtDesc(Long teamId);

    List<TeamMember> findByTeamIdAndLeftAtIsNull(Long teamId);

    List<TeamMember> findByEmployeeIdAndLeftAtIsNull(Long employeeId);

    Optional<TeamMember> findFirstByTeamIdAndEmployeeIdAndLeftAtIsNull(Long teamId, Long employeeId);
}
