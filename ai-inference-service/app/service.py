from __future__ import annotations

import hashlib
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

import numpy as np

from .schemas import CurveRequest, FeatureVO, InferenceResponse


@dataclass(frozen=True)
class ModelMetadata:
    version: str
    path: Path
    sha256: str | None = None
    input_name: str | None = None
    output_name: str | None = None


class InferenceProvider(Protocol):
    name: str
    model_version: str

    def predict(
        self, values: np.ndarray, evidence: np.ndarray
    ) -> tuple[str, float, list[str]]: ...


def auxiliary_features(request: CurveRequest) -> np.ndarray:
    ct = request.ct_value if request.ct_value is not None and request.ct_value > 0 else 0.0
    concentration = max(request.concentration_value or 0.0, 0.0)
    risk_score = 0.0 if (request.risk_level or "NORMAL").upper() == "NORMAL" else 1.0
    return np.asarray(
        [
            1.0 if ct > 0 else 0.0,
            ct,
            1.0 if request.concentration_value is not None else 0.0,
            np.log1p(concentration),
            risk_score,
        ],
        dtype=np.float32,
    )


def curve_features(values: np.ndarray, evidence: np.ndarray) -> FeatureVO:
    baseline = float(np.median(values[: max(3, len(values) // 5)]))
    peak_index = int(np.argmax(values))
    peak = float(values[peak_index])
    return FeatureVO(
        baseline=baseline,
        peak=peak,
        amplitude=peak - baseline,
        peak_index=peak_index,
        slope=float(np.max(np.diff(values))),
        ct_detected=float(evidence[0]),
        ct_value=float(evidence[1]),
        concentration_present=float(evidence[2]),
        log_concentration=float(evidence[3]),
        risk_score=float(evidence[4]),
    )


class OnnxRuntimeProvider:
    name = "onnxruntime"

    def __init__(self, metadata: ModelMetadata):
        import onnxruntime as ort

        if metadata.sha256:
            digest = hashlib.sha256(metadata.path.read_bytes()).hexdigest()
            if digest.lower() != metadata.sha256.lower():
                raise ValueError("MODEL_SHA256_MISMATCH")
        self.model_version = metadata.version
        self.session = ort.InferenceSession(
            str(metadata.path), providers=["CPUExecutionProvider"]
        )
        self.input_name = metadata.input_name or self.session.get_inputs()[0].name
        self.output_name = metadata.output_name or self.session.get_outputs()[0].name

    def predict(
        self, values: np.ndarray, evidence: np.ndarray
    ) -> tuple[str, float, list[str]]:
        # Contract: float32 tensor [batch, points]; output [batch, 4] logits ordered below.
        tensor = values.astype(np.float32)[None, :]
        inputs = {self.input_name: tensor}
        if len(self.session.get_inputs()) > 1:
            inputs[self.session.get_inputs()[1].name] = evidence[None, :].astype(np.float32)
        probabilities = self._probabilities(inputs)
        labels = ["NEGATIVE", "POSITIVE", "SUSPICIOUS", "INVALID"]
        index = int(np.argmax(probabilities))
        reason = (
            "COLD_START_WEAK_LABEL_LOGISTIC"
            if "cold-start" in self.model_version
            else "ONNX_CLASSIFIER"
        )
        evidence_reason = (
            "QUANTITATIVE_EVIDENCE_USED"
            if len(self.session.get_inputs()) > 1
            else "LEGACY_CURVE_ONLY_MODEL"
        )
        return labels[index], float(probabilities[index]), [reason, evidence_reason]

    def probabilities(self, values: np.ndarray, evidence: np.ndarray) -> np.ndarray:
        tensor = values.astype(np.float32)[None, :]
        inputs = {self.input_name: tensor}
        if len(self.session.get_inputs()) > 1:
            inputs[self.session.get_inputs()[1].name] = evidence[None, :].astype(np.float32)
        return self._probabilities(inputs)

    def _probabilities(self, inputs: dict[str, np.ndarray]) -> np.ndarray:
        logits = np.asarray(self.session.run([self.output_name], inputs)[0])[0]
        probabilities = np.exp(logits - np.max(logits))
        return probabilities / probabilities.sum()


class StructuredOnnxProvider:
    name = "structured-onnxruntime"
    targets = ["RSV", "ADV", "FluB", "HPIV", "S.P", "C.P", "MP", "HRV", "FluA", "nCoV", "H.I", "CoV"]

    def __init__(self, metadata: ModelMetadata):
        import onnxruntime as ort

        if metadata.sha256:
            digest = hashlib.sha256(metadata.path.read_bytes()).hexdigest()
            if digest.lower() != metadata.sha256.lower():
                raise ValueError("STRUCTURED_MODEL_SHA256_MISMATCH")
        self.model_version = metadata.version
        self.session = ort.InferenceSession(
            str(metadata.path), providers=["CPUExecutionProvider"]
        )

    def probabilities(self, target_code: str, evidence: np.ndarray) -> np.ndarray | None:
        if target_code not in self.targets:
            return None
        target = np.zeros((1, len(self.targets)), dtype=np.float32)
        target[0, self.targets.index(target_code)] = 1.0
        logits = np.asarray(
            self.session.run(
                ["logits"],
                {"target": target, "evidence": evidence[None, :].astype(np.float32)},
            )[0]
        )[0]
        probabilities = np.exp(logits - np.max(logits))
        return probabilities / probabilities.sum()


class RuleBaselineProvider:
    """Explicit degraded fallback. This is not a trained model."""

    name = "rule-baseline"
    model_version = "rule-baseline-v1"

    def predict(
        self, values: np.ndarray, evidence: np.ndarray
    ) -> tuple[str, float, list[str]]:
        feature = curve_features(values, evidence)
        if not np.isfinite(values).all() or float(np.std(values)) == 0:
            return "INVALID", 0.99, ["NON_FINITE_OR_FLAT_CURVE"]
        if feature.amplitude >= 5 and feature.slope > 0:
            return "POSITIVE", min(0.99, 0.6 + feature.amplitude / 100), ["SIGNIFICANT_RISE"]
        if feature.amplitude >= 2:
            return "SUSPICIOUS", 0.65, ["BORDERLINE_AMPLITUDE"]
        return "NEGATIVE", 0.85, ["NO_SIGNIFICANT_RISE"]


class InferenceService:
    def __init__(
        self,
        provider: InferenceProvider,
        degradation_reasons: list[str] | None = None,
        structured_provider: StructuredOnnxProvider | None = None,
        structured_weight: float = 0.25,
    ):
        self.provider = provider
        self.degradation_reasons = degradation_reasons or []
        self.structured_provider = structured_provider
        self.structured_weight = min(max(structured_weight, 0.0), 0.5)

    @classmethod
    def from_environment(cls) -> "InferenceService":
        from .model_loader import resolve_model

        try:
            metadata = resolve_model()
            if not metadata:
                return cls(RuleBaselineProvider(), ["MODEL_PATH_NOT_CONFIGURED"])
            provider = OnnxRuntimeProvider(metadata)
            structured_provider = None
            structured_code = os.getenv("STRUCTURED_MODEL_CODE", "structured-classifier")
            try:
                structured_metadata = resolve_model(
                    structured_code,
                    "STRUCTURED_MODEL_PATH",
                    "STRUCTURED_MODEL_VERSION",
                    "STRUCTURED_MODEL_SHA256",
                )
                if structured_metadata:
                    structured_provider = StructuredOnnxProvider(structured_metadata)
            except Exception:
                structured_provider = None
            return cls(
                provider,
                structured_provider=structured_provider,
                structured_weight=float(os.getenv("STRUCTURED_MODEL_WEIGHT", "0.25")),
            )
        except Exception as failure:
            return cls(
                RuleBaselineProvider(),
                [f"ONNX_PROVIDER_UNAVAILABLE:{type(failure).__name__}"],
            )

    def infer(self, request: CurveRequest) -> InferenceResponse:
        values = np.asarray(request.values, dtype=float)
        evidence = auxiliary_features(request)
        features = curve_features(values, evidence)
        degraded = self.provider.name == "rule-baseline"
        if not np.isfinite(values).all() or float(np.std(values)) <= 1e-9:
            judgement, confidence, reasons = "INVALID", 0.99, ["SIGNAL_QC_INVALID"]
        else:
            judgement, confidence, reasons = self.provider.predict(values, evidence)
            if (
                self.structured_provider is not None
                and isinstance(self.provider, OnnxRuntimeProvider)
                and self.structured_weight > 0
            ):
                structured = self.structured_provider.probabilities(request.channel, evidence)
                if structured is not None:
                    curve = self.provider.probabilities(values, evidence)
                    probabilities = (
                        (1.0 - self.structured_weight) * curve
                        + self.structured_weight * structured
                    )
                    labels = ["NEGATIVE", "POSITIVE", "SUSPICIOUS", "INVALID"]
                    index = int(np.argmax(probabilities))
                    judgement = labels[index]
                    confidence = float(probabilities[index])
                    reasons = [*reasons, "STRUCTURED_WEAK_LABEL_FUSION"]
            if not degraded and judgement in {"POSITIVE", "NEGATIVE"} and confidence < 0.60:
                judgement = "SUSPICIOUS"
                reasons = [*reasons, "LOW_MODEL_CONFIDENCE"]
        return InferenceResponse(
            sample_id=request.sample_id,
            judgement=judgement,
            confidence=confidence,
            reason_codes=reasons,
            inference_logic=self._explain(judgement, confidence, reasons, features),
            features=features,
            provider=self.provider.name,
            model_version=self.provider.model_version,
            degraded=degraded,
            degradation_reasons=self.degradation_reasons,
        )

    def _explain(
        self, judgement: str, confidence: float, reasons: list[str], features: FeatureVO
    ) -> str:
        reason_text = {
            "ONNX_CLASSIFIER": "ONNX model assigned the highest class probability",
            "COLD_START_WEAK_LABEL_LOGISTIC": (
                "cold-start logistic ONNX model trained from system judgement weak labels "
                "assigned the highest class probability"
            ),
            "QUANTITATIVE_EVIDENCE_USED": "Ct, concentration and risk evidence participated in inference",
            "STRUCTURED_WEAK_LABEL_FUSION": (
                "historical target-specific Ct and concentration weak-label evidence was fused "
                "as an auxiliary score"
            ),
            "LEGACY_CURVE_ONLY_MODEL": "the active legacy model only supports curve evidence",
            "SIGNAL_QC_INVALID": "the raw curve is non-finite or flat and failed signal quality control",
            "LOW_MODEL_CONFIDENCE": "the highest model probability is below the 0.60 review threshold",
            "NON_FINITE_OR_FLAT_CURVE": "the raw curve contains invalid values or has no variation",
            "SIGNIFICANT_RISE": "the raw curve has a significant rising amplitude and positive slope",
            "BORDERLINE_AMPLITUDE": "the raw curve amplitude is in the suspicious interval",
            "NO_SIGNIFICANT_RISE": "the raw curve has no significant fluorescence rise",
        }
        evidence = "; ".join(reason_text.get(code, code) for code in reasons)
        return (
            f"Raw uncorrected curve classified as {judgement} with confidence {confidence:.4f}; "
            f"baseline={features.baseline:.4f}, peak={features.peak:.4f}, "
            f"amplitude={features.amplitude:.4f}, maxSlope={features.slope:.4f}, "
            f"peakIndex={features.peak_index}, ctDetected={bool(features.ct_detected)}, "
            f"ct={features.ct_value:.4f}, logConcentration={features.log_concentration:.4f}, "
            f"riskScore={features.risk_score:.0f}. Basis: {evidence}."
        )


inference_service = InferenceService.from_environment()
