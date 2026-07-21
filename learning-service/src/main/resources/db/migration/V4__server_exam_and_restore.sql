CREATE TABLE exam_question (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, course_code VARCHAR(80) NOT NULL,
 stem VARCHAR(500) NOT NULL, options_json JSON NOT NULL, correct_options_json JSON NOT NULL,
 score INT NOT NULL, enabled BOOLEAN NOT NULL DEFAULT TRUE,
 KEY idx_question_course(course_code, enabled));
CREATE TABLE exam_attempt (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, assignment_id BIGINT NOT NULL, reviewer_id VARCHAR(64) NOT NULL,
 status VARCHAR(24) NOT NULL, score DECIMAL(5,2) NOT NULL DEFAULT 0,
 started_at TIMESTAMP(6) NOT NULL, submitted_at TIMESTAMP(6),
 KEY idx_attempt_assignment(assignment_id, reviewer_id));
CREATE TABLE exam_answer (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, attempt_id BIGINT NOT NULL, question_id BIGINT NOT NULL,
 selected_options_json JSON NOT NULL, correct BOOLEAN NOT NULL, awarded_score INT NOT NULL,
 UNIQUE KEY uk_attempt_question(attempt_id, question_id));
CREATE TABLE learning_outbox (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, event_id VARCHAR(80) NOT NULL, event_type VARCHAR(64) NOT NULL,
 aggregate_id VARCHAR(64) NOT NULL, payload_json JSON NOT NULL, status VARCHAR(24) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL, UNIQUE KEY uk_learning_outbox_event(event_id));
INSERT INTO exam_question(course_code,stem,options_json,correct_options_json,score,enabled) VALUES
('COURSE-FN-001','出现疑似假阴性曲线时应采取哪些措施？','["A.复查原始曲线","B.直接放行","C.核对质控","D.记录复核依据"]','["A","C","D"]',50,TRUE),
('COURSE-FN-001','三方判读冲突的最终依据是什么？','["A.AI结果","B.复核真值","C.仪器结果","D.多数票"]','["B"]',50,TRUE),
('COURSE-QC-001','质控无效时能否出具检测结论？','["A.可以","B.不可以"]','["B"]',100,TRUE);
