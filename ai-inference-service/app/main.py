from fastapi import FastAPI
from . import service
from .incremental_training import train_incremental
from .schemas import (
    CurveRequest,
    IncrementalTrainingRequest,
    IncrementalTrainingResponse,
    InferenceResponse,
)

app = FastAPI(title="AI Audit Inference", version="1.0.0")

@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}

@app.post("/v1/inference/curves", response_model=InferenceResponse)
def curve_inference(request: CurveRequest) -> InferenceResponse:
    return service.inference_service.infer(request)


@app.post("/v1/training/incremental", response_model=IncrementalTrainingResponse)
def incremental_training(
    request: IncrementalTrainingRequest,
) -> IncrementalTrainingResponse:
    result = train_incremental(request)
    if request.activate and result.status in {"TRAINED", "ALREADY_COMPLETED"}:
        service.inference_service = service.InferenceService.from_environment()
    return result
