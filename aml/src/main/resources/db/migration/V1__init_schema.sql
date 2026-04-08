-- ============================================================
-- V1: AML Screening initial schema for PostgreSQL
-- Schema: l4pc_aml
-- ============================================================

-- Screening audit log: every screening request is recorded
CREATE TABLE screening_audit (
    id              VARCHAR(64)     NOT NULL PRIMARY KEY,
    customer_id     VARCHAR(64)     NOT NULL,
    screening_type  VARCHAR(32)     NOT NULL,  -- INITIAL, ONGOING, EVENT_TRIGGERED, MANUAL
    request_input   TEXT            NOT NULL,  -- JSON: original screening request payload
    provider_output TEXT,                      -- JSON: raw provider response
    match_count     INTEGER         NOT NULL DEFAULT 0,
    risk_score      INTEGER,                   -- 0-100
    risk_level      VARCHAR(16),               -- LOW, MEDIUM, HIGH
    status          VARCHAR(16)     NOT NULL,  -- CLEAR, ALERT, ERROR
    trace_id        VARCHAR(64),
    actor           VARCHAR(128),
    source_system   VARCHAR(64),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_screening_audit_customer ON screening_audit(customer_id);
CREATE INDEX idx_screening_audit_status ON screening_audit(status);
CREATE INDEX idx_screening_audit_created ON screening_audit(created_at);

-- Screening match: individual matches found per screening
CREATE TABLE screening_match (
    id              VARCHAR(64)     NOT NULL PRIMARY KEY,
    screening_id    VARCHAR(64)     NOT NULL REFERENCES screening_audit(id),
    provider        VARCHAR(32)     NOT NULL,  -- DOW_JONES, REFINITIV, EU_SANCTIONS, UN_SANCTIONS, INTERNAL
    match_score     INTEGER         NOT NULL,  -- 0-100
    match_type      VARCHAR(32)     NOT NULL,  -- EXACT, FUZZY, ALIAS, PHONETIC
    category        VARCHAR(32)     NOT NULL,  -- SANCTION, PEP, ADVERSE_MEDIA, WATCHLIST
    entity_id       VARCHAR(128),              -- external entity reference from provider
    entity_name     VARCHAR(512),
    entity_type     VARCHAR(16),               -- PERSON, ENTITY
    details_json    TEXT,                       -- JSON: full match details from provider
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_screening_match_screening ON screening_match(screening_id);
CREATE INDEX idx_screening_match_category ON screening_match(category);

-- Alert: generated when screening produces high-risk matches
CREATE TABLE alert (
    id              VARCHAR(64)     NOT NULL PRIMARY KEY,
    screening_id    VARCHAR(64)     NOT NULL REFERENCES screening_audit(id),
    customer_id     VARCHAR(64)     NOT NULL,
    alert_type      VARCHAR(32)     NOT NULL,  -- SANCTION_HIT, PEP_HIT, ADVERSE_MEDIA, HIGH_RISK
    risk_level      VARCHAR(16)     NOT NULL,  -- MEDIUM, HIGH
    status          VARCHAR(32)     NOT NULL DEFAULT 'OPEN',  -- OPEN, UNDER_REVIEW, ESCALATED, APPROVED, REJECTED
    assigned_to     VARCHAR(128),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_alert_customer ON alert(customer_id);
CREATE INDEX idx_alert_status ON alert(status);

-- Case decision: audit trail for alert review decisions
CREATE TABLE case_decision (
    id              VARCHAR(64)     NOT NULL PRIMARY KEY,
    alert_id        VARCHAR(64)     NOT NULL REFERENCES alert(id),
    decision        VARCHAR(32)     NOT NULL,  -- APPROVE, REJECT, ESCALATE
    decision_reason TEXT            NOT NULL,
    decided_by      VARCHAR(128)    NOT NULL,
    approved_by     VARCHAR(128),              -- second approver for maker-checker
    attachments     TEXT,                       -- JSON array of attachment references
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_case_decision_alert ON case_decision(alert_id);
