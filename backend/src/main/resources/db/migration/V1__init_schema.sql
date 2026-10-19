CREATE TABLE roles (
    id CHAR(36) NOT NULL,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_roles_name UNIQUE (name)
);

CREATE TABLE permissions (
    id CHAR(36) NOT NULL,
    code VARCHAR(80) NOT NULL,
    description VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_permissions_code UNIQUE (code)
);

CREATE TABLE role_permissions (
    role_id CHAR(36) NOT NULL,
    permission_id CHAR(36) NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id)
);

CREATE TABLE users (
    id CHAR(36) NOT NULL,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL,
    failed_attempt_count INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE user_roles (
    user_id CHAR(36) NOT NULL,
    role_id CHAR(36) NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE TABLE refresh_tokens (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE customers (
    id CHAR(36) NOT NULL,
    customer_number VARCHAR(20) NOT NULL,
    first_name VARCHAR(80) NOT NULL,
    last_name VARCHAR(80) NOT NULL,
    email VARCHAR(160) NOT NULL,
    phone VARCHAR(30) NULL,
    date_of_birth DATE NOT NULL,
    address_line VARCHAR(160) NOT NULL,
    city VARCHAR(80) NOT NULL,
    country VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL,
    registered_at TIMESTAMP NOT NULL,
    created_by CHAR(36) NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_customers_number UNIQUE (customer_number),
    CONSTRAINT uq_customers_email UNIQUE (email),
    CONSTRAINT fk_customers_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT chk_customers_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE accounts (
    id CHAR(36) NOT NULL,
    account_number VARCHAR(20) NOT NULL,
    customer_id CHAR(36) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    balance DECIMAL(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    opened_at TIMESTAMP NOT NULL,
    closed_at TIMESTAMP NULL,
    created_by CHAR(36) NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_accounts_number UNIQUE (account_number),
    CONSTRAINT fk_accounts_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_accounts_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT chk_accounts_balance CHECK (balance >= 0),
    CONSTRAINT chk_accounts_type CHECK (account_type IN ('SAVINGS', 'CHECKING')),
    CONSTRAINT chk_accounts_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT chk_accounts_currency CHECK (currency = 'USD')
);

CREATE TABLE transactions (
    id CHAR(36) NOT NULL,
    reference_number VARCHAR(30) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    source_account_id CHAR(36) NULL,
    destination_account_id CHAR(36) NULL,
    idempotency_key VARCHAR(80) NULL,
    request_hash VARCHAR(64) NULL,
    description VARCHAR(255) NULL,
    created_by CHAR(36) NULL,
    created_at TIMESTAMP NOT NULL,
    decided_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_transactions_reference UNIQUE (reference_number),
    CONSTRAINT uq_transactions_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_transactions_source FOREIGN KEY (source_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_transactions_destination FOREIGN KEY (destination_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_transactions_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT chk_transactions_amount CHECK (amount > 0),
    CONSTRAINT chk_transactions_type CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    CONSTRAINT chk_transactions_status CHECK (status IN ('COMPLETED', 'PENDING_APPROVAL', 'REJECTED'))
);

CREATE TABLE transaction_approvals (
    id CHAR(36) NOT NULL,
    transaction_id CHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_by CHAR(36) NOT NULL,
    decided_by CHAR(36) NULL,
    decision_comment VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL,
    decided_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_transaction_approvals_tx UNIQUE (transaction_id),
    CONSTRAINT fk_approvals_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id),
    CONSTRAINT fk_approvals_requested_by FOREIGN KEY (requested_by) REFERENCES users (id),
    CONSTRAINT fk_approvals_decided_by FOREIGN KEY (decided_by) REFERENCES users (id),
    CONSTRAINT chk_approvals_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE TABLE audit_logs (
    id CHAR(36) NOT NULL,
    actor_user_id CHAR(36) NULL,
    actor_username VARCHAR(50) NOT NULL,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(36) NULL,
    success BOOLEAN NOT NULL,
    message VARCHAR(1000) NOT NULL,
    ip_address VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES users (id)
);

CREATE TABLE number_sequences (
    sequence_name VARCHAR(50) NOT NULL,
    next_value BIGINT NOT NULL,
    PRIMARY KEY (sequence_name)
);

CREATE TABLE system_settings (
    setting_key VARCHAR(100) NOT NULL,
    setting_value VARCHAR(500) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    updated_by VARCHAR(36) NULL,
    PRIMARY KEY (setting_key),
    CONSTRAINT fk_settings_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
);

CREATE INDEX idx_customers_last_name ON customers (last_name);
CREATE INDEX idx_customers_registered_at ON customers (registered_at);
CREATE INDEX idx_accounts_customer ON accounts (customer_id);
CREATE INDEX idx_accounts_status ON accounts (status);
CREATE INDEX idx_transactions_created_at ON transactions (created_at);
CREATE INDEX idx_transactions_source ON transactions (source_account_id);
CREATE INDEX idx_transactions_destination ON transactions (destination_account_id);
CREATE INDEX idx_transactions_status ON transactions (status);
CREATE INDEX idx_audit_created_at ON audit_logs (created_at);
CREATE INDEX idx_audit_actor ON audit_logs (actor_user_id);
CREATE INDEX idx_refresh_user ON refresh_tokens (user_id);
