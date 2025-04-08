CREATE TABLE node_integrations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL,
    client_code VARCHAR(64) NOT NULL,
    target VARCHAR(64),
    secondary_target VARCHAR(64),
    in_source TEXT,
    in_source_type VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(64)
);