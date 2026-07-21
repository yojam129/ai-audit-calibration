from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path

import httpx
from minio import Minio


def sha256(path: Path) -> str:
    digest = hashlib.sha256(path.read_bytes())
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--artifact-dir", default="artifacts/curve-classifier/0.2.0-evidence-cold-start")
    parser.add_argument("--model-code", default="curve-classifier")
    parser.add_argument("--version", default="0.2.0-evidence-cold-start")
    parser.add_argument("--registry-url", default=os.getenv("MODEL_REGISTRY_URL", "http://localhost:18094"))
    args = parser.parse_args()

    artifact_dir = Path(args.artifact_dir).resolve()
    model_path = artifact_dir / "model.onnx"
    metrics_path = artifact_dir / "metrics.json"
    metrics = json.loads(metrics_path.read_text(encoding="utf-8"))
    checksum = sha256(model_path)
    if checksum != metrics["modelSha256"]:
        raise ValueError("Model checksum differs from training metrics")

    endpoint = os.environ["MINIO_ENDPOINT"]
    secure = endpoint.startswith("https://")
    minio = Minio(
        endpoint.removeprefix("http://").removeprefix("https://"),
        access_key=os.environ["MINIO_ACCESS_KEY"],
        secret_key=os.environ["MINIO_SECRET_KEY"],
        secure=secure,
    )
    bucket = "ai-audit-models"
    if not minio.bucket_exists(bucket):
        minio.make_bucket(bucket)
    prefix = f"{args.model_code}/{args.version}"
    minio.fput_object(bucket, f"{prefix}/model.onnx", str(model_path))
    minio.fput_object(bucket, f"{prefix}/metrics.json", str(metrics_path), content_type="application/json")

    registry = args.registry_url.rstrip("/")
    response = httpx.post(
        f"{registry}/api/models",
        json={
            "modelCode": args.model_code,
            "version": args.version,
            "runtime": "ONNX_RUNTIME",
            "artifactUri": f"minio://{bucket}/{prefix}/model.onnx",
            "checksum": checksum,
            "metricsJson": json.dumps(metrics, ensure_ascii=False),
        },
        timeout=30,
    )
    response.raise_for_status()
    payload = response.json()
    if payload.get("code") != 0:
        raise ValueError(f"Model registration failed: {payload}")
    model_id = payload["data"]["id"]
    deployment = httpx.post(
        f"{registry}/api/models/{model_id}/deployment",
        params={"trafficPercent": 100},
        timeout=30,
    )
    deployment.raise_for_status()
    deployed = deployment.json()
    if deployed.get("code") != 0:
        raise ValueError(f"Model activation failed: {deployed}")
    print(json.dumps(deployed["data"], ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
