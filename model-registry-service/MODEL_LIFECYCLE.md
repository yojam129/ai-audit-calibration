# Model Lifecycle

The model registry is intentionally not a human Flowable workflow today. Incremental training is
an automatic technical pipeline controlled by `AI_TRAINING_AUTO_ACTIVATE` and
`AI_TRAINING_TRAFFIC_PERCENT`:

1. Train an ONNX artifact from archived truth feedback.
2. Upload the artifact and metrics to MinIO.
3. Verify SHA-256 and register an immutable model version.
4. Activate canary or full traffic according to configuration.
5. Preserve the previous version for automatic rollback.

These transitions are audited in `model_lifecycle_audit`. A future human compliance approval can
be inserted after `VALIDATED`; artifact verification, traffic switching, and rollback remain
technical operations even in that design.
