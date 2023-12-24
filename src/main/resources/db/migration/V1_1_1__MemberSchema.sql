CREATE TABLE IF NOT EXISTS members_table(
    id SERIAL PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL
);