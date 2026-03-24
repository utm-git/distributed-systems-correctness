DROP TABLE IF EXISTS shared_resource CASCADE;
DROP TABLE IF EXISTS fencing_tokens CASCADE;

CREATE TABLE shared_resource (
    resource_id VARCHAR(255) PRIMARY KEY,
    value VARCHAR(255) NOT NULL,
    last_fencing_token BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE fencing_tokens (
    resource_id VARCHAR(255) PRIMARY KEY,
    current_token BIGINT NOT NULL DEFAULT 0
);

-- Initialize a resource
INSERT INTO shared_resource (resource_id, value, last_fencing_token) VALUES ('config-file-123', 'initial_value', 0);
INSERT INTO fencing_tokens (resource_id, current_token) VALUES ('config-file-123', 0);
