-- API keys
CREATE TABLE api_key (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    key_prefix VARCHAR(8) NOT NULL,
    key_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    bootstrap BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    created_by_principal_type VARCHAR(20) NULL,
    created_by_principal_id VARCHAR(120) NULL,
    last_used_at TIMESTAMP NULL
);
CREATE INDEX idx_api_key_role_enabled ON api_key(role, enabled);
CREATE INDEX idx_api_key_prefix ON api_key(key_prefix);

-- Verification profiles
CREATE TABLE verification_profile (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    description TEXT NULL,
    preset VARCHAR(20) NOT NULL,
    policy_xml TEXT NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Validation jobs
CREATE TABLE validation_job (
    id UUID PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    original_status VARCHAR(20) NULL,
    profile_id UUID NULL REFERENCES verification_profile(id),
    profile_overrides TEXT NULL,
    reports_requested VARCHAR(100) NOT NULL,
    document_path VARCHAR(500) NULL,
    document_filename VARCHAR(255) NULL,
    result_path VARCHAR(500) NULL,
    callback_url VARCHAR(500) NULL,
    callback_secret_cipher TEXT NULL,
    callback_algorithm VARCHAR(20) NULL,
    callback_attempts INT NOT NULL DEFAULT 0,
    next_callback_at TIMESTAMP NULL,
    last_callback_error TEXT NULL,
    pickup_attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    delivered_at TIMESTAMP NULL,
    expires_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP NULL,
    error_message TEXT NULL,
    requested_by_principal_type VARCHAR(20) NOT NULL,
    requested_by_principal_id VARCHAR(120) NOT NULL,
    last_accessed_at TIMESTAMP NULL
);
CREATE INDEX idx_validation_job_status_next_callback ON validation_job(status, next_callback_at);
CREATE INDEX idx_validation_job_status_pickup ON validation_job(status, pickup_attempts);
CREATE INDEX idx_validation_job_status_expires ON validation_job(status, expires_at);
CREATE INDEX idx_validation_job_principal_status
    ON validation_job(requested_by_principal_type, requested_by_principal_id, status);

-- Trusted certificates (mirror)
CREATE TABLE trusted_certificate (
    id UUID PRIMARY KEY,
    fingerprint_sha256 VARCHAR(64) NOT NULL UNIQUE,
    ski VARCHAR(64) NULL,
    aki VARCHAR(64) NULL,
    subject_dn VARCHAR(500) NULL,
    subject_cn VARCHAR(255) NULL,
    issuer_dn VARCHAR(500) NULL,
    issuer_cn VARCHAR(255) NULL,
    serial_number VARCHAR(80) NULL,
    country VARCHAR(8) NULL,
    tsp_name VARCHAR(255) NULL,
    tsp_service_type VARCHAR(255) NULL,
    tsp_service_status VARCHAR(80) NULL,
    valid_from TIMESTAMP NULL,
    valid_to TIMESTAMP NULL,
    certificate_der_b64 TEXT NOT NULL,
    tsl_url VARCHAR(500) NULL,
    last_seen_at TIMESTAMP NOT NULL,
    removed_at TIMESTAMP NULL
);
CREATE INDEX idx_trusted_cert_ski ON trusted_certificate(ski);
CREATE INDEX idx_trusted_cert_aki ON trusted_certificate(aki);
CREATE INDEX idx_trusted_cert_subject_cn ON trusted_certificate(subject_cn);
CREATE INDEX idx_trusted_cert_subject_dn ON trusted_certificate(subject_dn);
CREATE INDEX idx_trusted_cert_issuer_dn ON trusted_certificate(issuer_dn);
CREATE INDEX idx_trusted_cert_serial ON trusted_certificate(serial_number);
CREATE INDEX idx_trusted_cert_country ON trusted_certificate(country);
CREATE INDEX idx_trusted_cert_tsp_name ON trusted_certificate(tsp_name);
CREATE INDEX idx_trusted_cert_tsp_service_type ON trusted_certificate(tsp_service_type);
CREATE INDEX idx_trusted_cert_tsp_service_status ON trusted_certificate(tsp_service_status);
CREATE INDEX idx_trusted_cert_validity ON trusted_certificate(valid_from, valid_to);

-- TSL refresh history
CREATE TABLE tsl_refresh (
    id UUID PRIMARY KEY,
    trigger VARCHAR(20) NOT NULL,
    triggered_by_principal_type VARCHAR(20) NULL,
    triggered_by_principal_id VARCHAR(120) NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP NULL,
    status VARCHAR(20) NOT NULL,
    sources_total INT NOT NULL DEFAULT 0,
    sources_failed INT NOT NULL DEFAULT 0,
    certificates_added INT NOT NULL DEFAULT 0,
    certificates_removed INT NOT NULL DEFAULT 0,
    certificates_unchanged INT NOT NULL DEFAULT 0,
    error_summary TEXT NULL
);
CREATE INDEX idx_tsl_refresh_started ON tsl_refresh(started_at DESC);

-- Audit log
CREATE TABLE audit_log (
    id UUID PRIMARY KEY,
    occurred_at TIMESTAMP NOT NULL,
    principal_type VARCHAR(20) NOT NULL,
    principal_id VARCHAR(120) NOT NULL,
    action VARCHAR(60) NOT NULL,
    target_type VARCHAR(40) NULL,
    target_id VARCHAR(120) NULL,
    success BOOLEAN NOT NULL,
    details TEXT NULL,
    ip_address VARCHAR(64) NULL
);
CREATE INDEX idx_audit_occurred ON audit_log(occurred_at DESC);
CREATE INDEX idx_audit_principal ON audit_log(principal_id);
CREATE INDEX idx_audit_action ON audit_log(action);

-- ShedLock
CREATE TABLE shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
