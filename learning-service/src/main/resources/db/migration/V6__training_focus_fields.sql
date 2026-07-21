ALTER TABLE learning_assignment
    ADD COLUMN focus_sample_id CHAR(36) NULL,
    ADD COLUMN focus_sample_no VARCHAR(64) NULL,
    ADD COLUMN focus_chamber VARCHAR(8) NULL,
    ADD COLUMN focus_channel_code VARCHAR(32) NULL,
    ADD COLUMN focus_target_code VARCHAR(64) NULL;
