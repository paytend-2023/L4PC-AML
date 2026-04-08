-- ============================================================
-- V2: Watchlist entities + customer screening tracking
-- ============================================================

-- Watchlist entity: imported from Dow Jones / other providers
CREATE TABLE watchlist_entity (
    id              VARCHAR(64)     NOT NULL PRIMARY KEY,
    provider        VARCHAR(32)     NOT NULL,  -- DOW_JONES, REFINITIV, EU_SANCTIONS, UN_SANCTIONS, INTERNAL
    external_id     VARCHAR(128),              -- provider's entity ID
    entity_type     VARCHAR(16)     NOT NULL,  -- PERSON, ENTITY
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, INACTIVE
    category        VARCHAR(32)     NOT NULL,  -- SANCTION, PEP, ADVERSE_MEDIA, WATCHLIST
    profile_notes   TEXT,
    source_list     VARCHAR(256),              -- e.g. "OFAC SDN", "EU Consolidated"
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_watchlist_entity_provider ON watchlist_entity(provider);
CREATE INDEX idx_watchlist_entity_category ON watchlist_entity(category);
CREATE INDEX idx_watchlist_entity_status ON watchlist_entity(status);

-- Watchlist name: names and aliases for a watchlist entity
CREATE TABLE watchlist_name (
    id              VARCHAR(64)     NOT NULL PRIMARY KEY,
    entity_id       VARCHAR(64)     NOT NULL REFERENCES watchlist_entity(id) ON DELETE CASCADE,
    name_type       VARCHAR(32)     NOT NULL DEFAULT 'PRIMARY',  -- PRIMARY, ALIAS, MAIDEN, FORMER
    full_name       VARCHAR(512),
    first_name      VARCHAR(256),
    surname         VARCHAR(256),
    middle_name     VARCHAR(256),
    entity_name     VARCHAR(512),              -- for ENTITY type
    normalized_name VARCHAR(512),              -- lowercased, stripped for search
    soundex_code    VARCHAR(16),               -- pre-computed Soundex
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_watchlist_name_entity ON watchlist_name(entity_id);
CREATE INDEX idx_watchlist_name_normalized ON watchlist_name(normalized_name);
CREATE INDEX idx_watchlist_name_soundex ON watchlist_name(soundex_code);

-- Watchlist identity: DOB, nationality, ID numbers
CREATE TABLE watchlist_identity (
    id              VARCHAR(64)     NOT NULL PRIMARY KEY,
    entity_id       VARCHAR(64)     NOT NULL REFERENCES watchlist_entity(id) ON DELETE CASCADE,
    date_of_birth   VARCHAR(32),               -- may be partial: "1985", "1985-01", "1985-01-15"
    nationality     VARCHAR(8),                -- ISO 3166-1 alpha-2
    id_type         VARCHAR(64),               -- PASSPORT, NATIONAL_ID, etc.
    id_number       VARCHAR(128),
    country_of_issue VARCHAR(8),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_watchlist_identity_entity ON watchlist_identity(entity_id);
CREATE INDEX idx_watchlist_identity_nationality ON watchlist_identity(nationality);
CREATE INDEX idx_watchlist_identity_id_number ON watchlist_identity(id_number);

-- Screening customer: tracks which customers are enrolled for ongoing screening
CREATE TABLE screening_customer (
    id              VARCHAR(64)     NOT NULL PRIMARY KEY,
    customer_id     VARCHAR(64)     NOT NULL,
    customer_name   VARCHAR(512)    NOT NULL,
    date_of_birth   VARCHAR(32),
    nationality     VARCHAR(8),
    id_number       VARCHAR(128),
    risk_level      VARCHAR(16)     NOT NULL DEFAULT 'LOW',  -- LOW, MEDIUM, HIGH
    screening_frequency VARCHAR(16) NOT NULL DEFAULT 'WEEKLY',  -- DAILY, WEEKLY, MONTHLY
    last_screened_at TIMESTAMP,
    next_screening_at TIMESTAMP,
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, INACTIVE
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_screening_customer UNIQUE (customer_id)
);

CREATE INDEX idx_screening_customer_next ON screening_customer(next_screening_at);
CREATE INDEX idx_screening_customer_status ON screening_customer(status);
CREATE INDEX idx_screening_customer_risk ON screening_customer(risk_level);

-- Data sync record: tracks provider data imports
CREATE TABLE data_sync_record (
    id              VARCHAR(64)     NOT NULL PRIMARY KEY,
    provider        VARCHAR(32)     NOT NULL,
    sync_type       VARCHAR(16)     NOT NULL,  -- FULL, INCREMENTAL
    file_name       VARCHAR(256),
    status          VARCHAR(16)     NOT NULL,  -- RUNNING, SUCCESS, FAILED
    records_processed INTEGER       NOT NULL DEFAULT 0,
    records_added   INTEGER         NOT NULL DEFAULT 0,
    records_updated INTEGER         NOT NULL DEFAULT 0,
    error_message   TEXT,
    started_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMP
);

CREATE INDEX idx_data_sync_provider ON data_sync_record(provider);
