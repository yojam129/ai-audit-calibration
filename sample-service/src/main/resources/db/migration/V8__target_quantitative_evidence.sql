ALTER TABLE instrument_run
    ADD COLUMN panel_code VARCHAR(64) NULL AFTER instrument_no,
    ADD COLUMN instrument_type VARCHAR(64) NULL AFTER panel_code,
    ADD COLUMN qc_status VARCHAR(24) NULL AFTER status,
    ADD COLUMN qc_evidence_json JSON NULL AFTER qc_status,
    ADD COLUMN target_mapping_json JSON NULL AFTER qc_evidence_json,
    ADD COLUMN overall_result_json JSON NULL AFTER target_mapping_json;

ALTER TABLE target_judgement
    ADD COLUMN channel_code VARCHAR(32) NULL AFTER chamber,
    ADD COLUMN concentration_value DECIMAL(20,6) NULL AFTER ct_value,
    ADD COLUMN concentration_unit VARCHAR(32) NULL AFTER concentration_value,
    ADD COLUMN risk_level VARCHAR(24) NOT NULL DEFAULT 'NORMAL' AFTER concentration_unit,
    ADD COLUMN risk_flags VARCHAR(255) NULL AFTER risk_level;

CREATE INDEX idx_target_pathogen_risk
    ON target_judgement(target_code, risk_level, created_at);
