CREATE TABLE registered_agents (
                                   id              UUID            NOT NULL PRIMARY KEY,
                                   name            VARCHAR(255),
                                   ip_address      VARCHAR(255),
                                   port            INTEGER         NOT NULL,
                                   state           VARCHAR(50)     NOT NULL,
                                   public_id       UUID            UNIQUE,
                                   registered_at   TIMESTAMP,
                                   requested_name  VARCHAR(255),
                                   approved_at     TIMESTAMP,
                                   last_seen_at    TIMESTAMP,
                                   approval_pushed BOOLEAN         NOT NULL DEFAULT FALSE,
                                   CONSTRAINT uq_agent_ip_port UNIQUE (ip_address, port)
);

CREATE TABLE peer_list_version (
                                   id      INTEGER PRIMARY KEY AUTOINCREMENT,
                                   version BIGINT      DEFAULT 1
);

CREATE TABLE pending_rename (
                                id             UUID        NOT NULL PRIMARY KEY,
                                agent_id       VARCHAR(255),
                                current_name   VARCHAR(255),
                                requested_name VARCHAR(255),
                                requested_at   TIMESTAMP
);

CREATE TABLE transfer_logs (
                               id              UUID        NOT NULL PRIMARY KEY,
                               from_agent_id   UUID,
                               from_agent_name VARCHAR(255),
                               to_agent_id     UUID,
                               to_agent_name   VARCHAR(255),
                               filename        VARCHAR(255),
                               file_size       BIGINT,
                               transferred_at  TIMESTAMP
);

CREATE TABLE file_transfer (
                               transfer_id      VARCHAR(36)   NOT NULL PRIMARY KEY,
                               source_agent_id  VARCHAR(36)   NOT NULL,
                               target_agent_id  VARCHAR(36),
                               file_name        VARCHAR(255)  NOT NULL,
                               file_path        VARCHAR(1024),
                               file_size        BIGINT        NOT NULL,
                               confirmed_offset BIGINT        NOT NULL DEFAULT 0,
                               status           VARCHAR(50)   NOT NULL,
                               retry_count      INTEGER       NOT NULL DEFAULT 0,
                               max_retries      INTEGER       NOT NULL DEFAULT 5,
                               created_at       TIMESTAMP,
                               last_chunk_at    TIMESTAMP,
                               expires_at       TIMESTAMP,
                               failure_reason   VARCHAR(1024),
                               target_ip        VARCHAR(255),
                               target_port      INTEGER       NOT NULL DEFAULT 0
);