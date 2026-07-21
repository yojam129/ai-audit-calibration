ALTER TABLE instrument_run
    ADD COLUMN module_position VARCHAR(32) NULL AFTER instrument_no;
