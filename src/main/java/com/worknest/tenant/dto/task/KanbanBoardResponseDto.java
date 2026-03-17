package com.worknest.tenant.dto.task;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
public class KanbanBoardResponseDto {
    private Long projectId;
    private List<KanbanColumnDto> columns;
    private Map<String, Long> summary;
}
