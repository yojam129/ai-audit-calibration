CREATE TABLE scheduler_job_execution(
 id BIGINT PRIMARY KEY AUTO_INCREMENT,execution_key VARCHAR(128) NOT NULL UNIQUE,
 job_name VARCHAR(80) NOT NULL,parameter_json TEXT,status VARCHAR(16) NOT NULL,
 started_at TIMESTAMP(6) NOT NULL,finished_at TIMESTAMP(6),duration_ms BIGINT NOT NULL DEFAULT 0,
 result_text TEXT,error_text TEXT,KEY idx_scheduler_job(job_name,started_at)
);
