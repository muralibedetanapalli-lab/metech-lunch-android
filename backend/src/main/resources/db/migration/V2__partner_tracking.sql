-- Live delivery tracking: the partner app reports GPS fixes while a batch is out for delivery.

ALTER TABLE delivery_batches ADD COLUMN picked_up_at TIMESTAMPTZ;

CREATE TABLE partner_locations (
    id            BIGSERIAL PRIMARY KEY,
    partner_id    VARCHAR(36)      NOT NULL REFERENCES users(id),
    latitude      DOUBLE PRECISION NOT NULL,
    longitude     DOUBLE PRECISION NOT NULL,
    accuracy_m    REAL,
    speed_mps     REAL,
    heading_deg   REAL,
    recorded_at   TIMESTAMPTZ      NOT NULL,   -- when the phone took the fix
    received_at   TIMESTAMPTZ      NOT NULL
);

CREATE INDEX partner_locations_partner_time ON partner_locations (partner_id, recorded_at DESC);
