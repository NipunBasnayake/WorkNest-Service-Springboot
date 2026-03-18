package com.worknest.tenant.repository;

import com.worknest.tenant.entity.TaskComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {

    List<TaskComment> findByTaskIdOrderByCreatedAtAsc(Long taskId);

    void deleteByTaskId(Long taskId);
}
