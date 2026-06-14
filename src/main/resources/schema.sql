-- Runs automatically on startup (spring.sql.init.mode=always)

CREATE TABLE IF NOT EXISTS payments (
    txn_id          VARCHAR(64)  PRIMARY KEY,
    merchant_id     VARCHAR(64)  NOT NULL,
    amount          NUMERIC(19,2) NOT NULL,
    currency        VARCHAR(3)   NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    idempotency_key VARCHAR(128) UNIQUE,
    updated_by      VARCHAR(64),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_payments_merchant_id ON payments (merchant_id);
CREATE INDEX IF NOT EXISTS idx_payments_status      ON payments (status);
