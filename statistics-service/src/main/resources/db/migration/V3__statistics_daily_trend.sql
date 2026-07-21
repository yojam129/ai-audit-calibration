CREATE TABLE daily_accuracy_projection (
    projection_key VARCHAR(40) PRIMARY KEY,
    metric_date DATE NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    correct_count BIGINT NOT NULL DEFAULT 0,
    total_count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_daily_accuracy(metric_date, source_type)
);
