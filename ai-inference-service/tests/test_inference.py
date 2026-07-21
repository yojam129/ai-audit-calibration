import pytest
from pydantic import ValidationError

from app.schemas import CurveRequest
from app.service import InferenceService, RuleBaselineProvider


def test_rule_provider_is_explicitly_degraded():
    service = InferenceService(RuleBaselineProvider(), ["MODEL_PATH_NOT_CONFIGURED"])
    result = service.infer(
        CurveRequest(sample_id="S1", channel="FAM", values=[0, 0, 1, 4, 8])
    )
    assert result.provider == "rule-baseline"
    assert result.model_version == "rule-baseline-v1"
    assert result.degraded is True
    assert result.degradation_reasons == ["MODEL_PATH_NOT_CONFIGURED"]


def test_request_rejects_derived_or_unknown_fields():
    with pytest.raises(ValidationError):
        CurveRequest(
            sample_id="S1",
            channel="FAM",
            values=[0, 1, 2, 3, 4],
            baseline=2.0,
        )


def test_quantitative_evidence_is_derived_and_explained():
    service = InferenceService(RuleBaselineProvider(), ["MODEL_PATH_NOT_CONFIGURED"])
    result = service.infer(
        CurveRequest(
            sample_id="S2",
            channel="FAM",
            values=[0, 0, 1, 4, 8],
            ct_value=36.2,
            concentration_value=450.0,
            concentration_unit="Copies/mL",
            risk_level="WATCH",
            risk_flags=["BORDERLINE_CT", "LOW_CONCENTRATION"],
        )
    )
    assert result.features.ct_detected == 1
    assert result.features.concentration_present == 1
    assert result.features.risk_score == 1
    assert "ct=36.2000" in result.inference_logic
    assert "logConcentration=" in result.inference_logic
