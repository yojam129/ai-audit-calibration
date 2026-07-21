ALTER TABLE review_task
    ADD COLUMN primary_reviewer_id VARCHAR(64) NULL AFTER sample_id,
    ADD COLUMN primary_auth_user_id BIGINT NULL AFTER primary_reviewer_id,
    ADD COLUMN primary_duration_ms BIGINT NOT NULL DEFAULT 0 AFTER primary_auth_user_id;
