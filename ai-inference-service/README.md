# AI inference service

This service **does not train a model**. It loads a previously trained ONNX artifact through one of:

1. `MODEL_PATH`, `MODEL_VERSION` and optional `MODEL_SHA256`;
2. `MODEL_REGISTRY_URL` current-model metadata followed by a `minio://bucket/object` download.

MinIO credentials are environment variables (`MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`,
`MINIO_SECRET_KEY`, `MINIO_SECURE`) and must not be committed.

## ONNX contract

- Inputs: raw curve `float32[batch, points]` and quantitative evidence `float32[batch, 5]` containing
  Ct detection, Ct value, concentration presence, log concentration and risk score. Legacy
  curve-only ONNX models remain readable but are marked in the inference evidence.
- Output: one logits tensor shaped `[batch, 4]`; columns must be
  `NEGATIVE, POSITIVE, SUSPICIOUS, INVALID`; default output is the first output or
  `MODEL_OUTPUT_NAME`.
- The registry SHA-256 is verified before a session is created.
- Requests accept raw curve values plus instrument-produced Ct, concentration and risk fields.
  Pydantic rejects caller-supplied derived features; normalization is performed by this service.

Real models must be exported to this contract, evaluated outside this service, uploaded to MinIO,
then registered and activated through `model-registry-service`.

## Cold-start training

`training/train_cold_start.py` builds the demo-only `0.2.0-evidence-cold-start` model from the
fluorescence workbook. It uses the 45-point raw curves together with Ct, concentration and derived
risk evidence. The workbook's system judgement is the weak training label.

The script groups the train/test split by instrument run, embeds curve and quantitative-evidence
preprocessing inside the ONNX graph, verifies ONNX prediction parity, and writes `model.onnx` plus
`metrics.json`.
The resulting metrics measure agreement with instrument weak labels, not clinical accuracy.

```bash
pip install -r requirements-train.txt
python training/train_cold_start.py
python training/publish_model.py
```

`publish_model.py` creates the `ai-audit-models` MinIO bucket, uploads the artifact and metrics,
registers the SHA-256-verified version, and activates it at 100 percent traffic. It requires the
MinIO and model-registry environment variables documented in `.env.example`.

At inference time, flat or non-finite curves are marked `INVALID`. ONNX positive/negative results
below 0.60 confidence are marked `SUSPICIOUS`. Both policies are included in `reason_codes` and the
human-readable inference logic.

If the ONNX runtime/artifact is unavailable, the service returns the transparent
`rule-baseline` provider with `degraded=true` and populated `degradation_reasons`. The baseline is
deterministic business logic, not a trained model, and must not be used to claim model performance.

## Secondary-truth incremental training

`POST /v1/training/incremental` accepts only feedback samples where the AI label differs from
the secondary final truth. Every sample includes the immutable source model version, raw curve,
Ct/concentration evidence, curve checksum and final target label.

The deterministic training key produces a deterministic candidate version, so retries register
the same artifact. Training requires at least `AI_TRAINING_MIN_SAMPLES` same-length curves and at
least two final-truth classes. The produced metrics retain all feedback keys and source model
versions. Registration and activation use `model-registry-service`; activating a new current model
does not rewrite historical inference rows or model lifecycle audit entries.

## Structured weak-label auxiliary model

`training/train_structured_cold_start.py` trains `structured-classifier` from the positive-rate
warning workbook. It expands each detection order into target-level rows and uses target code, Ct,
concentration and a quantitative risk flag. The workbook detection result is a historical system
weak label, not final clinical truth.

The inference service loads the active `structured-classifier` independently and fuses its class
probabilities into the curve model at `STRUCTURED_MODEL_WEIGHT` (default `0.25`, capped at `0.50`).
The curve model remains authoritative and its version remains the inference audit version. If the
structured model is absent, curve inference continues without fusion.

```bash
python training/train_structured_cold_start.py \
  --input ../../demo数据-阳性率预警.xlsx \
  --output artifacts/structured-classifier/0.1.0-warning-weak-label
python training/publish_model.py \
  --artifact-dir artifacts/structured-classifier/0.1.0-warning-weak-label \
  --model-code structured-classifier \
  --version 0.1.0-warning-weak-label
```

Perfect agreement against this weak label can reflect the instrument rule relationship between Ct
and reported results. It must not be presented as clinical accuracy.

Run:

```bash
uvicorn app.main:app --host 0.0.0.0 --port 18000
pytest
```
