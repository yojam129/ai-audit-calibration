from __future__ import annotations

import os
from pathlib import Path
from urllib.parse import urlparse

import httpx

from .service import ModelMetadata


def resolve_model(
    model_code: str | None = None,
    path_variable: str = "MODEL_PATH",
    version_variable: str = "MODEL_VERSION",
    sha_variable: str = "MODEL_SHA256",
) -> ModelMetadata | None:
    local_path = os.getenv(path_variable)
    if local_path:
        return ModelMetadata(
            version=os.getenv(version_variable, "unregistered"),
            path=Path(local_path),
            sha256=os.getenv(sha_variable),
            input_name=os.getenv("MODEL_INPUT_NAME"),
            output_name=os.getenv("MODEL_OUTPUT_NAME"),
        )
    registry_url = os.getenv("MODEL_REGISTRY_URL")
    resolved_model_code = model_code or os.getenv("MODEL_CODE", "curve-classifier")
    if not registry_url:
        return None
    response = httpx.get(
        f"{registry_url.rstrip('/')}/api/models/current",
        params={"modelCode": resolved_model_code},
        timeout=10,
    )
    response.raise_for_status()
    metadata = response.json()["data"]
    artifact_uri = metadata["artifactUri"]
    if not artifact_uri.startswith("minio://"):
        raise ValueError("REGISTRY_ARTIFACT_URI_MUST_BE_MINIO")
    parsed = urlparse(artifact_uri)
    destination = (
        Path(os.getenv("MODEL_CACHE_DIR", ".model-cache"))
        / resolved_model_code
        / metadata["version"]
        / "model.onnx"
    )
    destination.parent.mkdir(parents=True, exist_ok=True)
    if not destination.exists():
        from minio import Minio

        endpoint = os.environ["MINIO_ENDPOINT"].removeprefix("http://").removeprefix("https://")
        client = Minio(
            endpoint,
            access_key=os.environ["MINIO_ACCESS_KEY"],
            secret_key=os.environ["MINIO_SECRET_KEY"],
            secure=os.getenv("MINIO_SECURE", "false").lower() == "true",
        )
        client.fget_object(parsed.netloc, parsed.path.lstrip("/"), str(destination))
    return ModelMetadata(
        version=metadata["version"],
        path=destination,
        sha256=metadata["checksum"],
        input_name=os.getenv("MODEL_INPUT_NAME"),
        output_name=os.getenv("MODEL_OUTPUT_NAME"),
    )
