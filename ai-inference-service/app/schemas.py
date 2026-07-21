from typing import Literal

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class CurveRequest(BaseModel):
    """Raw instrument evidence only; all derived features are computed by the service."""

    model_config = ConfigDict(extra="forbid")
    sample_id: str = Field(min_length=1, max_length=128)
    channel: str = Field(min_length=1, max_length=64)
    values: list[float] = Field(min_length=5, max_length=100_000)
    ct_value: float | None = None
    concentration_value: float | None = Field(default=None, ge=0)
    concentration_unit: str | None = Field(default=None, max_length=32)
    risk_level: str | None = Field(default=None, max_length=24)
    risk_flags: list[str] = Field(default_factory=list, max_length=16)


class FeatureVO(BaseModel):
    baseline: float
    peak: float
    amplitude: float
    peak_index: int
    slope: float
    ct_detected: float
    ct_value: float
    concentration_present: float
    log_concentration: float
    risk_score: float


class InferenceResponse(BaseModel):
    sample_id: str
    judgement: Literal["POSITIVE", "NEGATIVE", "SUSPICIOUS", "INVALID"]
    confidence: float = Field(ge=0, le=1)
    reason_codes: list[str]
    inference_logic: str
    features: FeatureVO
    provider: str
    model_version: str
    degraded: bool
    degradation_reasons: list[str]


class IncrementalTrainingSample(BaseModel):
    model_config = ConfigDict(extra="forbid", alias_generator=to_camel, populate_by_name=True)
    feedback_key: str = Field(min_length=1, max_length=256)
    sample_id: str = Field(min_length=1, max_length=128)
    run_no: str = Field(min_length=1, max_length=128)
    curve_id: str = Field(min_length=1, max_length=128)
    chamber: str = Field(min_length=1, max_length=16)
    channel_code: str = Field(min_length=1, max_length=64)
    target_code: str = Field(min_length=1, max_length=64)
    ai_label: str = Field(min_length=1, max_length=32)
    truth_label: Literal["POSITIVE", "NEGATIVE", "INDETERMINATE", "INVALID"]
    source_model_version: str | None = Field(default=None, max_length=128)
    raw_values: list[float] = Field(min_length=5, max_length=100_000)
    ct_value: float | None = None
    concentration_value: float | None = Field(default=None, ge=0)
    concentration_unit: str | None = Field(default=None, max_length=32)
    risk_level: str | None = Field(default=None, max_length=24)
    risk_flags: list[str] = Field(default_factory=list, max_length=16)
    curve_checksum: str = Field(min_length=64, max_length=64)


class IncrementalTrainingRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", alias_generator=to_camel, populate_by_name=True)
    training_key: str = Field(min_length=1, max_length=128)
    model_code: str = Field(min_length=1, max_length=64)
    base_model_version: str = Field(min_length=1, max_length=128)
    activate: bool = True
    traffic_percent: int = Field(default=100, ge=0, le=100)
    samples: list[IncrementalTrainingSample] = Field(min_length=1, max_length=100_000)


class IncrementalTrainingResponse(BaseModel):
    status: Literal["TRAINED", "ALREADY_COMPLETED", "SKIPPED"]
    training_key: str
    model_version: str | None = None
    model_id: int | None = None
    sample_count: int
    detail: str
