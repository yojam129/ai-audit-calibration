ALTER TABLE alert
    ADD COLUMN alert_logic VARCHAR(2000) NULL AFTER reason_codes;
