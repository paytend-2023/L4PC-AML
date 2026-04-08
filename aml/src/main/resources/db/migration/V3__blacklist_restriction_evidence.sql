-- ============================================================
-- V3: Blacklist, Country Restriction, Org Restriction, Evidence
-- Schema: l4pc_aml
-- ============================================================

-- Blacklist entries (user + counterparty)
CREATE TABLE blacklist_entry (
    id              VARCHAR(40)  PRIMARY KEY,
    identifier_type VARCHAR(30)  NOT NULL,
    identifier_value VARCHAR(200) NOT NULL,
    entity_name     VARCHAR(200),
    reason          VARCHAR(500),
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by      VARCHAR(100),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    disabled_at     TIMESTAMP,
    disabled_by     VARCHAR(100)
);
CREATE INDEX idx_bl_identifier ON blacklist_entry (identifier_type, identifier_value);
CREATE INDEX idx_bl_status ON blacklist_entry (status);

-- Risk country configuration
CREATE TABLE risk_country (
    id               VARCHAR(40)  PRIMARY KEY,
    country_code     VARCHAR(2)   NOT NULL,
    country_name     VARCHAR(100),
    restriction_type VARCHAR(30)  NOT NULL,
    scope            VARCHAR(30),
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    reason           VARCHAR(500),
    created_by       VARCHAR(100),
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_rc_country_type ON risk_country (country_code, restriction_type);
CREATE INDEX idx_rc_status ON risk_country (status);

-- Organization registration restriction
CREATE TABLE org_restriction (
    id                VARCHAR(40)  PRIMARY KEY,
    country_code      VARCHAR(2)   NOT NULL,
    restriction_field VARCHAR(30)  NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    reason            VARCHAR(500),
    created_by        VARCHAR(100),
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_or_country_field ON org_restriction (country_code, restriction_field);

-- AML evaluation evidence (immutable audit trail)
CREATE TABLE aml_evidence (
    id             VARCHAR(40)  PRIMARY KEY,
    evaluation_id  VARCHAR(40)  NOT NULL,
    customer_id    VARCHAR(80)  NOT NULL,
    trace_id       VARCHAR(80)  NOT NULL,
    check_type     VARCHAR(30)  NOT NULL,
    hit            BOOLEAN      NOT NULL DEFAULT FALSE,
    reason_code    VARCHAR(60),
    hit_type       VARCHAR(30),
    entity_id      VARCHAR(200),
    entity_name    VARCHAR(200),
    provider       VARCHAR(60),
    match_score    INTEGER      DEFAULT 0,
    detail_json    TEXT,
    actor          VARCHAR(100),
    source_system  VARCHAR(100),
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_ev_eval_id ON aml_evidence (evaluation_id);
CREATE INDEX idx_ev_customer ON aml_evidence (customer_id);
CREATE INDEX idx_ev_trace ON aml_evidence (trace_id);
