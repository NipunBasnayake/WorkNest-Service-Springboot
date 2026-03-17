package com.worknest.tenant.entity;

import com.worknest.tenant.enums.ChatType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "chat_read_receipts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_chat_read_receipts_unique",
                        columnNames = {"chat_type", "message_id", "employee_id"}
                )
        },
        indexes = {
                @Index(name = "idx_chat_read_receipts_message", columnList = "chat_type,message_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ChatReadReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "chat_type", nullable = false, length = 20)
    private ChatType chatType;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt;
}
