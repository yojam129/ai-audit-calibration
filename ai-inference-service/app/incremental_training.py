from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path

import httpx
import numpy as np
import onnx
from minio import Minio
from onnx import TensorProto, helper, numpy_helper
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler

from .schemas import IncrementalTrainingRequest, IncrementalTrainingResponse


LABELS = ["NEGATIVE", "POSITIVE", "SUSPICIOUS", "INVALID"]
TRUTH_TO_CLASS = {
    "NEGATIVE": 0,
    "POSITIVE": 1,
    "INDETERMINATE": 2,
    "INVALID": 3,
}


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _evidence(sample) -> list[float]:
    ct = sample.ct_value if sample.ct_value is not None and sample.ct_value > 0 else 0.0
    concentration = max(sample.concentration_value or 0.0, 0.0)
    risk_score = 0.0 if (sample.risk_level or "NORMAL").upper() == "NORMAL" else 1.0
    return [
        1.0 if ct > 0 else 0.0,
        ct,
        1.0 if sample.concentration_value is not None else 0.0,
        float(np.log1p(concentration)),
        risk_score,
    ]


def _export_model(
    path: Path,
    scaler: StandardScaler,
    classifier: LogisticRegression,
    points: int,
) -> None:
    feature_count = points + 5
    coefficient = np.zeros((feature_count, 4), dtype=np.float32)
    intercept = np.full(4, -20.0, dtype=np.float32)
    classes = classifier.classes_.astype(int)
    if len(classes) == 2 and classifier.coef_.shape[0] == 1:
        coefficient[:, classes[0]] = -classifier.coef_[0].astype(np.float32) / 2
        coefficient[:, classes[1]] = classifier.coef_[0].astype(np.float32) / 2
        intercept[classes[0]] = -float(classifier.intercept_[0]) / 2
        intercept[classes[1]] = float(classifier.intercept_[0]) / 2
    else:
        for row, label in enumerate(classes):
            coefficient[:, label] = classifier.coef_[row].astype(np.float32)
            intercept[label] = float(classifier.intercept_[row])
    initializers = [
        numpy_helper.from_array(scaler.mean_.astype(np.float32), "mean"),
        numpy_helper.from_array(scaler.scale_.astype(np.float32), "scale"),
        numpy_helper.from_array(coefficient, "coefficient"),
        numpy_helper.from_array(intercept, "intercept"),
        numpy_helper.from_array(np.asarray([0], dtype=np.int64), "slice_starts"),
        numpy_helper.from_array(np.asarray([10], dtype=np.int64), "slice_ends"),
        numpy_helper.from_array(np.asarray([1], dtype=np.int64), "slice_axes"),
        numpy_helper.from_array(np.asarray([1], dtype=np.int64), "slice_steps"),
    ]
    nodes = [
        helper.make_node(
            "Slice",
            ["curve", "slice_starts", "slice_ends", "slice_axes", "slice_steps"],
            ["baseline_points"],
        ),
        helper.make_node("ReduceMean", ["baseline_points"], ["baseline"], axes=[1], keepdims=1),
        helper.make_node("Sub", ["curve", "baseline"], ["corrected"]),
        helper.make_node("Concat", ["corrected", "evidence"], ["combined"], axis=1),
        helper.make_node("Sub", ["combined", "mean"], ["centered"]),
        helper.make_node("Div", ["centered", "scale"], ["standardized"]),
        helper.make_node("MatMul", ["standardized", "coefficient"], ["linear"]),
        helper.make_node("Add", ["linear", "intercept"], ["logits"]),
    ]
    graph = helper.make_graph(
        nodes,
        "secondary-truth-incremental-classifier",
        [
            helper.make_tensor_value_info("curve", TensorProto.FLOAT, [None, points]),
            helper.make_tensor_value_info("evidence", TensorProto.FLOAT, [None, 5]),
        ],
        [helper.make_tensor_value_info("logits", TensorProto.FLOAT, [None, 4])],
        initializers,
    )
    model = helper.make_model(
        graph,
        producer_name="ai-audit-secondary-truth-training",
        opset_imports=[helper.make_opsetid("", 17)],
    )
    model.ir_version = 9
    onnx.checker.check_model(model)
    path.parent.mkdir(parents=True, exist_ok=True)
    onnx.save(model, path)


def _publish(
    request: IncrementalTrainingRequest,
    version: str,
    model_path: Path,
    metrics_path: Path,
) -> tuple[int, str]:
    endpoint = os.environ["MINIO_ENDPOINT"]
    minio = Minio(
        endpoint.removeprefix("http://").removeprefix("https://"),
        access_key=os.environ["MINIO_ACCESS_KEY"],
        secret_key=os.environ["MINIO_SECRET_KEY"],
        secure=endpoint.startswith("https://"),
    )
    bucket = os.getenv("MODEL_BUCKET", "ai-audit-models")
    if not minio.bucket_exists(bucket):
        minio.make_bucket(bucket)
    prefix = f"{request.model_code}/{version}"
    minio.fput_object(bucket, f"{prefix}/model.onnx", str(model_path))
    minio.fput_object(
        bucket, f"{prefix}/metrics.json", str(metrics_path), content_type="application/json"
    )
    registry = os.environ["MODEL_REGISTRY_URL"].rstrip("/")
    checksum = _sha256(model_path)
    registered = httpx.post(
        f"{registry}/api/models",
        json={
            "modelCode": request.model_code,
            "version": version,
            "runtime": "ONNX_RUNTIME",
            "artifactUri": f"minio://{bucket}/{prefix}/model.onnx",
            "checksum": checksum,
            "metricsJson": metrics_path.read_text(encoding="utf-8"),
        },
        timeout=30,
    )
    registered.raise_for_status()
    payload = registered.json()
    if payload.get("code") != 0:
        raise ValueError(f"model registration failed: {payload}")
    model_id = int(payload["data"]["id"])
    status = payload["data"]["status"]
    if request.activate and status != "ACTIVE":
        deployed = httpx.post(
            f"{registry}/api/models/{model_id}/deployment",
            params={"trafficPercent": request.traffic_percent},
            timeout=30,
        )
        deployed.raise_for_status()
        deployment_payload = deployed.json()
        if deployment_payload.get("code") != 0:
            raise ValueError(f"model activation failed: {deployment_payload}")
        status = deployment_payload["data"]["status"]
    return model_id, status


def train_incremental(request: IncrementalTrainingRequest) -> IncrementalTrainingResponse:
    minimum = int(os.getenv("AI_TRAINING_MIN_SAMPLES", "5"))
    lengths: dict[int, int] = {}
    for sample in request.samples:
        lengths[len(sample.raw_values)] = lengths.get(len(sample.raw_values), 0) + 1
    points = max(lengths, key=lengths.get)
    samples = [sample for sample in request.samples if len(sample.raw_values) == points]
    labels = np.asarray([TRUTH_TO_CLASS[sample.truth_label] for sample in samples], dtype=np.int64)
    if len(samples) < minimum:
        return IncrementalTrainingResponse(
            status="SKIPPED",
            training_key=request.training_key,
            sample_count=len(samples),
            detail=f"requires at least {minimum} same-length feedback samples",
        )
    if len(np.unique(labels)) < 2:
        return IncrementalTrainingResponse(
            status="SKIPPED",
            training_key=request.training_key,
            sample_count=len(samples),
            detail="requires at least two secondary-truth classes",
        )
    key_hash = hashlib.sha256(request.training_key.encode("utf-8")).hexdigest()
    version = f"truth-inc-{key_hash[:16]}"
    output = Path(os.getenv("AI_TRAINING_ARTIFACT_DIR", "artifacts/incremental")) / version
    model_path = output / "model.onnx"
    metrics_path = output / "metrics.json"
    already_built = model_path.exists() and metrics_path.exists()
    if not already_built:
        curves = np.asarray([sample.raw_values for sample in samples], dtype=np.float32)
        corrected = curves - curves[:, :10].mean(axis=1, keepdims=True)
        evidence = np.asarray([_evidence(sample) for sample in samples], dtype=np.float32)
        combined = np.concatenate([corrected, evidence], axis=1)
        scaler = StandardScaler().fit(combined)
        classifier = LogisticRegression(
            class_weight="balanced", max_iter=5000, random_state=42
        ).fit(scaler.transform(combined), labels)
        predicted = classifier.predict(scaler.transform(combined))
        _export_model(model_path, scaler, classifier, points)
        metrics = {
            "modelCode": request.model_code,
            "version": version,
            "trainingKey": request.training_key,
            "baseModelVersion": request.base_model_version,
            "labelSource": "ARCHIVED_FINAL_TRUTH_AI_ERRORS_ONLY",
            "sampleCount": len(samples),
            "truthClasses": sorted({LABELS[label] for label in labels}),
            "trainingAccuracy": float(np.mean(predicted == labels)),
            "feedbackKeys": [sample.feedback_key for sample in samples],
            "sourceModelVersions": sorted(
                {sample.source_model_version for sample in samples if sample.source_model_version}
            ),
            "modelSha256": _sha256(model_path),
            "warning": "Incremental candidate trained from archived final-truth AI errors; audit history remains immutable",
        }
        metrics_path.write_text(
            json.dumps(metrics, ensure_ascii=False, indent=2), encoding="utf-8"
        )
    model_id, deployment_status = _publish(
        request, version, model_path, metrics_path
    )
    return IncrementalTrainingResponse(
        status="ALREADY_COMPLETED" if already_built else "TRAINED",
        training_key=request.training_key,
        model_version=version,
        model_id=model_id,
        sample_count=len(samples),
        detail=f"registered model {model_id} with status {deployment_status}",
    )
