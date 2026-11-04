-- Initial schema for the Metech lunch platform.
-- Every operational record carries kitchen_id so more kitchens can be added later.

CREATE TABLE kitchens (
    id           VARCHAR(36) PRIMARY KEY,
    name         TEXT        NOT NULL,
    address      TEXT        NOT NULL,
    timezone     TEXT        NOT NULL,          -- e.g. Asia/Kolkata
    cutoff_time  TIME        NOT NULL,          -- same-day ordering cutoff, kitchen local time
    created_at   TIMESTAMPTZ NOT NULL
);

CREATE TABLE offices (
    id          VARCHAR(36) PRIMARY KEY,
    kitchen_id  VARCHAR(36) NOT NULL REFERENCES kitchens(id),
    name        TEXT        NOT NULL,
    address     TEXT        NOT NULL,
    active      BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE TABLE drop_points (
    id            VARCHAR(36) PRIMARY KEY,
    office_id     VARCHAR(36) NOT NULL REFERENCES offices(id),
    name          TEXT        NOT NULL,
    instructions  TEXT,
    active        BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE TABLE users (
    id                     VARCHAR(36) PRIMARY KEY,
    phone                  TEXT        NOT NULL,
    role                   TEXT        NOT NULL,   -- customer | delivery_partner | kitchen_staff | owner
    name                   TEXT        NOT NULL DEFAULT '',
    email                  TEXT,
    kitchen_id             VARCHAR(36) REFERENCES kitchens(id),  -- staff and partners
    default_office_id      VARCHAR(36) REFERENCES offices(id),
    default_drop_point_id  VARCHAR(36) REFERENCES drop_points(id),
    active                 BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ NOT NULL,
    UNIQUE (phone, role)
);

CREATE TABLE otp_codes (
    id          BIGSERIAL   PRIMARY KEY,
    phone       TEXT        NOT NULL,
    role        TEXT        NOT NULL,
    code_hash   TEXT        NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    attempts    INT         NOT NULL DEFAULT 0,
    consumed    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX otp_codes_phone_role_idx ON otp_codes (phone, role, created_at DESC);

CREATE TABLE refresh_tokens (
    token_hash  TEXT        PRIMARY KEY,
    user_id     VARCHAR(36) NOT NULL REFERENCES users(id),
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL
);

-- Meal catalogue maintained by the owner.
CREATE TABLE menu_items (
    id            VARCHAR(36) PRIMARY KEY,
    kitchen_id    VARCHAR(36) NOT NULL REFERENCES kitchens(id),
    name          TEXT        NOT NULL,
    description   TEXT        NOT NULL DEFAULT '',
    image_url     TEXT,
    price_paise   BIGINT      NOT NULL CHECK (price_paise >= 0),
    dietary_tags  TEXT        NOT NULL DEFAULT '[]',  -- JSON array, client-supplied
    allergens     TEXT        NOT NULL DEFAULT '[]',  -- JSON array, client-supplied
    active        BOOLEAN     NOT NULL DEFAULT TRUE
);

-- What is on offer on a given day, with its price and capacity for that day.
CREATE TABLE daily_menu_items (
    id            VARCHAR(36) PRIMARY KEY,
    kitchen_id    VARCHAR(36) NOT NULL REFERENCES kitchens(id),
    menu_date     DATE        NOT NULL,
    menu_item_id  VARCHAR(36) NOT NULL REFERENCES menu_items(id),
    price_paise   BIGINT      NOT NULL CHECK (price_paise >= 0),
    capacity      INT         CHECK (capacity IS NULL OR capacity >= 0),  -- NULL = unlimited
    available     BOOLEAN     NOT NULL DEFAULT TRUE,
    UNIQUE (kitchen_id, menu_date, menu_item_id)
);

CREATE TABLE orders (
    id               VARCHAR(36) PRIMARY KEY,
    kitchen_id       VARCHAR(36) NOT NULL REFERENCES kitchens(id),
    customer_id      VARCHAR(36) NOT NULL REFERENCES users(id),
    office_id        VARCHAR(36) NOT NULL REFERENCES offices(id),
    drop_point_id    VARCHAR(36) REFERENCES drop_points(id),
    delivery_date    DATE        NOT NULL,
    status           TEXT        NOT NULL,
    total_paise      BIGINT      NOT NULL,
    idempotency_key  TEXT        NOT NULL,
    delivery_otp     TEXT        NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    UNIQUE (customer_id, idempotency_key)
);
CREATE INDEX orders_kitchen_date_idx ON orders (kitchen_id, delivery_date, status);
CREATE INDEX orders_customer_idx ON orders (customer_id, created_at DESC);

CREATE TABLE order_lines (
    id                   BIGSERIAL   PRIMARY KEY,
    order_id             VARCHAR(36) NOT NULL REFERENCES orders(id),
    daily_menu_item_id   VARCHAR(36) NOT NULL REFERENCES daily_menu_items(id),
    menu_item_id         VARCHAR(36) NOT NULL REFERENCES menu_items(id),
    name                 TEXT        NOT NULL,
    quantity             INT         NOT NULL CHECK (quantity > 0),
    unit_price_paise     BIGINT      NOT NULL
);
CREATE INDEX order_lines_daily_item_idx ON order_lines (daily_menu_item_id);
CREATE INDEX order_lines_order_idx ON order_lines (order_id);

CREATE TABLE payments (
    id             VARCHAR(36) PRIMARY KEY,
    order_id       VARCHAR(36) NOT NULL UNIQUE REFERENCES orders(id),
    gateway        TEXT        NOT NULL,
    gateway_ref    TEXT        NOT NULL UNIQUE,
    checkout_url   TEXT,
    status         TEXT        NOT NULL,     -- pending | verified | failed | refunded
    refund_status  TEXT,                     -- requested | approved | rejected | completed
    amount_paise   BIGINT      NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL
);

-- Every gateway callback is recorded once; replays are ignored.
CREATE TABLE payment_events (
    id           BIGSERIAL   PRIMARY KEY,
    gateway      TEXT        NOT NULL,
    event_id     TEXT        NOT NULL,
    gateway_ref  TEXT        NOT NULL,
    payload      TEXT        NOT NULL,
    received_at  TIMESTAMPTZ NOT NULL,
    UNIQUE (gateway, event_id)
);

CREATE TABLE delivery_batches (
    id                 VARCHAR(36) PRIMARY KEY,
    kitchen_id         VARCHAR(36) NOT NULL REFERENCES kitchens(id),
    delivery_date      DATE        NOT NULL,
    office_id          VARCHAR(36) NOT NULL REFERENCES offices(id),
    drop_point_id      VARCHAR(36) REFERENCES drop_points(id),
    partner_id         VARCHAR(36) REFERENCES users(id),
    status             TEXT        NOT NULL,
    coordinator_name   TEXT,
    coordinator_phone  TEXT,
    coordinator_otp    TEXT,
    pickup_count       INT,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL
);
CREATE INDEX delivery_batches_partner_idx ON delivery_batches (partner_id, delivery_date DESC);

CREATE TABLE batch_orders (
    batch_id  VARCHAR(36) NOT NULL REFERENCES delivery_batches(id),
    order_id  VARCHAR(36) NOT NULL UNIQUE REFERENCES orders(id),
    PRIMARY KEY (batch_id, order_id)
);

CREATE TABLE delivery_issues (
    id          BIGSERIAL   PRIMARY KEY,
    batch_id    VARCHAR(36) NOT NULL REFERENCES delivery_batches(id),
    partner_id  VARCHAR(36) NOT NULL REFERENCES users(id),
    order_ids   TEXT        NOT NULL,   -- JSON array
    reason      TEXT        NOT NULL,
    partial     BOOLEAN     NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);

-- Key administrative changes and order status actions.
CREATE TABLE audit_log (
    id          BIGSERIAL   PRIMARY KEY,
    actor_id    VARCHAR(36),
    action      TEXT        NOT NULL,
    entity      TEXT        NOT NULL,
    entity_id   TEXT        NOT NULL,
    details     TEXT,
    created_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX audit_log_entity_idx ON audit_log (entity, entity_id);
