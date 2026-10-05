CREATE TABLE user_invite (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token       VARCHAR(64)  NOT NULL UNIQUE,
    email       VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX user_invite_user_id_idx ON user_invite (user_id);
CREATE INDEX user_invite_expires_at_idx ON user_invite (expires_at);

INSERT INTO app_setting (setting_key, setting_value, description, secret, updated_at)
SELECT v.setting_key, v.setting_value, v.description, v.secret, NOW()
FROM (VALUES
    ('invite.ttl-days', '2', 'Срок действия invite-ссылки в днях', FALSE),
    ('invite.public-base-url', 'http://localhost:8080', 'Публичный URL приложения для invite-ссылок', FALSE)
) AS v(setting_key, setting_value, description, secret)
WHERE NOT EXISTS (
    SELECT 1 FROM app_setting s WHERE s.setting_key = v.setting_key
);
