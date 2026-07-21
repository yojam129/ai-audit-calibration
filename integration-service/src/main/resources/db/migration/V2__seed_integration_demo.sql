INSERT INTO file_asset
    (id, asset_no, bucket_name, object_key, original_name, content_type,
     size_bytes, sha256, status, created_at)
VALUES
    (1, 'ASSET-DEMO-001', 'ai-audit-incoming', 'demo/sample-import.xlsx',
     '样本导入演示.xlsx',
     'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
     24576, REPEAT('0', 64), 'READY', CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT INTO import_batch
    (id, batch_no, asset_id, business_type, template_version, status,
     total_rows, success_rows, error_rows, version, created_at, updated_at)
VALUES
    (1, 'BATCH-DEMO-001', 1, 'SAMPLE', '1.0', 'COMPLETED',
     100, 98, 2, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE
    status = VALUES(status),
    total_rows = VALUES(total_rows),
    success_rows = VALUES(success_rows),
    error_rows = VALUES(error_rows);

INSERT INTO import_error
    (batch_id, row_no, column_name, error_code, error_message, created_at)
SELECT 1, 18, 'sampleNo', 'DUPLICATE_SAMPLE', '样本编号重复', CURRENT_TIMESTAMP(3)
WHERE NOT EXISTS (
    SELECT 1 FROM import_error
    WHERE batch_id = 1 AND row_no = 18 AND error_code = 'DUPLICATE_SAMPLE'
);
