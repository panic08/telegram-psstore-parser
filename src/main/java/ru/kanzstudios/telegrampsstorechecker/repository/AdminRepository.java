package ru.kanzstudios.telegrampsstorechecker.repository;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.kanzstudios.telegrampsstorechecker.model.Admin;

@Repository
public interface AdminRepository extends CrudRepository<Admin, Long> {
    @Query("SELECT a.* FROM admins_table a WHERE a.telegram_user_id = :telegramUserId")
    Admin findByTelegramUserId(@Param("telegramUserId") long telegramUserId);
}
