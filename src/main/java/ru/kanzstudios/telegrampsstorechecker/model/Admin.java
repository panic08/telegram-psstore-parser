package ru.kanzstudios.telegrampsstorechecker.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table(name = "admins_table")
public class Admin {
    @Id
    private Long id;
    @Column("telegram_user_id")
    private Long telegramUserId;
}
