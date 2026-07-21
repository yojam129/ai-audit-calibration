INSERT INTO model_version
    (id, model_code, version, runtime, artifact_uri, checksum,
     metrics_json, status, traffic_percent, created_at)
VALUES
    (1, 'curve-classifier', '1.0.0', 'ONNX_RUNTIME',
     'minio://ai-audit-models/curve-classifier/1.0.0/model.onnx',
     REPEAT('c', 64),
     JSON_OBJECT('accuracy', 0.936, 'sensitivity', 0.931, 'specificity', 0.941),
     'DEPLOYED', 100, CURRENT_TIMESTAMP(3)),
    (2, 'curve-classifier', '1.1.0-rc1', 'ONNX_RUNTIME',
     'minio://ai-audit-models/curve-classifier/1.1.0-rc1/model.onnx',
     REPEAT('d', 64),
     JSON_OBJECT('accuracy', 0.948, 'sensitivity', 0.944, 'specificity', 0.952),
     'VALIDATED', 0, CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE
    metrics_json = VALUES(metrics_json),
    status = VALUES(status),
    traffic_percent = VALUES(traffic_percent);
