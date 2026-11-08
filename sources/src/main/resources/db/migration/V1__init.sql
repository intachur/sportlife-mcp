CREATE TABLE visitor_snapshot (
    id BIGSERIAL PRIMARY KEY,
    club_id VARCHAR(32) NOT NULL,
    visitors INTEGER NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_visitor_snapshot_club_captured
    ON visitor_snapshot (club_id, captured_at DESC);

CREATE TABLE club_event (
    id BIGSERIAL PRIMARY KEY,
    club_id VARCHAR(32) NOT NULL,
    external_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    hall VARCHAR(255),
    trainer VARCHAR(255),
    event_date DATE NOT NULL,
    CONSTRAINT uq_club_event UNIQUE (club_id, external_id, starts_at)
);

CREATE INDEX idx_club_event_club_date
    ON club_event (club_id, event_date);
