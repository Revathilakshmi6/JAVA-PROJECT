-- Schema migration for Duplicate-Transaction Detection System (PS-62)

CREATE TABLE IF NOT EXISTS transaction (
    id              UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    payer_id        VARCHAR(64) NOT NULL,
    payee_id        VARCHAR(64) NOT NULL,
    amount          NUMERIC(19,2) NOT NULL,
    currency        CHAR(3) NOT NULL,
    idempotency_ref VARCHAR(128),
    status          VARCHAR(20) NOT NULL,   -- POSTED | SUPPRESSED | FLAGGED
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS duplicate_record (
    id                  UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    original_txn_id     UUID NOT NULL REFERENCES transaction(id),
    duplicate_txn_id    UUID NOT NULL REFERENCES transaction(id),
    match_tier          VARCHAR(20) NOT NULL,  -- EXACT | PROBABLE
    matched_fields      TEXT ARRAY,
    time_delta_ms       BIGINT NOT NULL,
    resolution          VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING | APPROVED | REJECTED
    resolved_by         VARCHAR(64),
    resolved_at         TIMESTAMP WITH TIME ZONE,
    detected_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_txn_window ON transaction (payer_id, payee_id, amount, created_at);
