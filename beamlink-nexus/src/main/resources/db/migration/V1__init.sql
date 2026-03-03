CREATE TABLE registered_agents (
    id              UUID            NOT NULL PRIMARY KEY,
    name            VARCHAR(255),
    approved_name   VARCHAR(255),
    ip_address      VARCHAR(255),
    port            INTEGER         NOT NULL,
    state           VARCHAR(50)     NOT NULL,
    auth_token      VARCHAR(512),
    public_token    VARCHAR(512),
    registered_at   TIMESTAMPTZ,
    requested_name  VARCHAR(255),
    approved_at     TIMESTAMPTZ,
    last_seen_at    TIMESTAMPTZ,
    approval_pushed BOOLEAN         NOT NULL DEFAULT FALSE,
    extra_origins   TEXT[],
    CONSTRAINT uq_agent_ip_port UNIQUE (ip_address, port)
);

CREATE TABLE peer_list_version (
    id      BIGSERIAL   NOT NULL PRIMARY KEY,
    version BIGINT      DEFAULT 1
);

CREATE TABLE pending_rename (
    id             UUID        NOT NULL PRIMARY KEY,
    agent_id       VARCHAR(255),
    current_name   VARCHAR(255),
    requested_name VARCHAR(255),
    requested_at   TIMESTAMPTZ
);

CREATE TABLE transfer_logs (
    id              UUID        NOT NULL PRIMARY KEY,
    from_agent_id   UUID,
    from_agent_name VARCHAR(255),
    to_agent_id     UUID,
    to_agent_name   VARCHAR(255),
    filename        VARCHAR(255),
    file_size       BIGINT,
    timestamp       TIMESTAMPTZ
);