ALTER TABLE detection_target_fact
    ADD COLUMN instrument_no VARCHAR(64) NULL AFTER order_id,
    ADD COLUMN panel_code VARCHAR(64) NULL AFTER instrument_no,
    ADD COLUMN reagent_lot_no VARCHAR(64) NULL AFTER panel_code,
    ADD COLUMN ct_value DECIMAL(10,4) NULL AFTER result_label,
    ADD COLUMN concentration_value DECIMAL(20,6) NULL AFTER ct_value,
    ADD COLUMN concentration_unit VARCHAR(32) NULL AFTER concentration_value,
    ADD COLUMN risk_level VARCHAR(24) NOT NULL DEFAULT 'NORMAL' AFTER concentration_unit;

CREATE INDEX idx_fact_quality_dimension
    ON detection_target_fact(organization_id, target_code, instrument_no, reagent_lot_no, occurred_at);
