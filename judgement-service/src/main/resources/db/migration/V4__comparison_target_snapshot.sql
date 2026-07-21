ALTER TABLE comparison_run
    ADD COLUMN targets_json JSON NULL AFTER reason_codes;
