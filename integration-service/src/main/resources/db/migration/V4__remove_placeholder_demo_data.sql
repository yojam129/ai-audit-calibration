DELETE FROM import_error
WHERE batch_id = 1
  AND error_code = 'DUPLICATE_SAMPLE';

DELETE FROM import_batch
WHERE id = 1
  AND batch_no = 'BATCH-DEMO-001';

DELETE FROM file_asset
WHERE id = 1
  AND asset_no = 'ASSET-DEMO-001';
