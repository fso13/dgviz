CREATE TABLE notification_transport (
    id           BIGSERIAL PRIMARY KEY,
    transport    VARCHAR(32)  NOT NULL UNIQUE,
    enabled      BOOLEAN      NOT NULL DEFAULT FALSE,
    config_json  TEXT         NOT NULL DEFAULT '{}',
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO notification_transport (transport, enabled, config_json) VALUES
    ('EMAIL', FALSE, '{"from":"","host":"","port":587,"username":"","password":"","starttls":true}'),
    ('TELEGRAM', FALSE, '{"botToken":"","chatId":""}');

CREATE TABLE notification_log (
    id            BIGSERIAL PRIMARY KEY,
    transport     VARCHAR(32)  NOT NULL,
    recipient     VARCHAR(500),
    subject       VARCHAR(500),
    status        VARCHAR(32)  NOT NULL,
    error_message TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX notification_log_created_idx ON notification_log (created_at DESC);
