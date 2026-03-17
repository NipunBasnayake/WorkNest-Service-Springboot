package com.worknest.tenant.service;

import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.dto.task.*;
import com.worknest.tenant.enums.TaskStatus;

import java.time.LocalDate;
import java.util.List;

public interface TaskService {

    TaskResponseDto createTask(TaskCreateRequestDto requestDto);

    TaskResponseDto updateTask(Long taskId, TaskUpdateRequestDto requestDto);

    TaskResponseDto changeStatus(Long taskId, TaskStatusUpdateRequestDto requestDto);

    TaskResponseDto changePriority(Long taskId, TaskPriorityUpdateRequestDto requestDto);

    TaskResponseDto changeAssignee(Long taskId, TaskAssigneeUpdateRequestDto requestDto);

    TaskResponseDto changeDueDate(Long taskId, TaskDueDateUpdateRequestDto requestDto);

    List<TaskResponseDto> listByProject(Long projectId);

    PagedResultDto<TaskResponseDto> listTasksPaged(
            Long projectId,
            TaskStatus status,
            Long assigneeId,
            LocalDate dueFrom,
            LocalDate dueTo,
            String search,
            int page,
            int size,
            String sortBy,
            String sortDir);

    List<TaskResponseDto> listByAssignee(Long assigneeId);

    List<TaskResponseDto> listByProjectAndStatus(Long projectId, TaskStatus status);

    TaskResponseDto getTaskById(Long taskId);

    void deleteTask(Long taskId);

    TaskCommentResponseDto addComment(Long taskId, TaskCommentCreateRequestDto requestDto);

    List<TaskCommentResponseDto> listComments(Long taskId);

    KanbanBoardResponseDto getKanbanBoard(Long projectId);
}
