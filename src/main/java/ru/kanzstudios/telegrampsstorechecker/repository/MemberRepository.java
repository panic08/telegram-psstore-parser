package ru.kanzstudios.telegrampsstorechecker.repository;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.kanzstudios.telegrampsstorechecker.model.Member;

@Repository
public interface MemberRepository extends CrudRepository<Member, Long> {

    @Query("SELECT m.* FROM members_table m WHERE m.telegram_user_id = :telegramUserId")
    Member findByTelegramUserId(@Param("telegramUserId") long telegramUserId);
}
