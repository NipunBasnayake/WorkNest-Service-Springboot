package com.worknest.tenant.dto.chat;

import com.worknest.tenant.dto.common.EmployeeSimpleDto;
import com.worknest.tenant.enums.ChatType;
import com.worknest.common.storage.StoredFileDto;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
public class HrMessageResponseDto {
    private Long id;
    private ChatType chatType;
    private Long conversationId;
    private EmployeeSimpleDto sender;
    private Long senderEmployeeId;
    private String senderName;
    private String message;
    private boolean read;
    private LocalDateTime createdAt;
    private List<StoredFileDto> attachments;
}
