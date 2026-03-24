DROP TABLE IF EXISTS saga_state CASCADE;
DROP TABLE IF EXISTS inventory CASCADE;
DROP TABLE IF EXISTS payments CASCADE;
DROP TABLE IF EXISTS shipments CASCADE;

CREATE TABLE saga_state (
    order_id UUID PRIMARY KEY,
    status VARCHAR(50) NOT NULL,
    current_step VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE inventory (
    item_id VARCHAR(50) PRIMARY KEY,
    quantity INT NOT NULL
);

CREATE TABLE payments (
    order_id UUID PRIMARY KEY,
    amount NUMERIC(10,2) NOT NULL,
    status VARCHAR(50) NOT NULL
);

CREATE TABLE shipments (
    order_id UUID PRIMARY KEY,
    status VARCHAR(50) NOT NULL
);

INSERT INTO inventory (item_id, quantity) VALUES ('ITEM_123', 100);
