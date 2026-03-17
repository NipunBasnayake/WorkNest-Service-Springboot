package com.worknest.tenant.repository;

import com.worknest.tenant.entity.ChatReadReceipt;
import com.worknest.tenant.enums.ChatType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatReadReceiptRepository extends JpaRepository<ChatReadReceipt, Long> {

    Optional<ChatReadReceipt> findByChatTypeAndMessageIdAndEmployeeId(
            ChatType chatType,
            Long messageId,
            Long employeeId);

    List<ChatReadReceipt> findByChatTypeAndMessageIdOrderByReadAtAsc(ChatType chatType, Long messageId);
}
