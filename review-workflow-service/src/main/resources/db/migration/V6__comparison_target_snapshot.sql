ALTER TABLE review_task
    ADD COLUMN source_targets_json JSON NULL AFTER consistency;
