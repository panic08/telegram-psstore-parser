package ru.kanzstudios.telegrampsstorechecker.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table(name = "members_table")
@Builder
public class Member {
    @Id
    private Long id;
    @Column("telegram_user_id")
    private Long telegramUserId;
}
