export function formatDateTime(value?: string | null) {
  if (!value) return "-";
  const normalized = value.replace("T", " ").replace(/Z$/, "");
  return normalized.slice(0, 19);
}

const statuses: Record<string, string> = {
  REGISTERED: "已登记",
  DETECTED: "已检测",
  CREATED: "已创建",
  IMPORTED: "已导入",
  PENDING: "待处理",
  OPEN: "待领取",
  CLAIMED: "已领取",
  IN_REVIEW: "审核中",
  SUBMITTED: "已提交",
  COMPARISON_PENDING: "待一致性比较",
  COMPARISON_COMPLETED: "比较完成",
  FINALIZED: "已终审",
  ARCHIVED: "已归档",
  ESCALATED: "已升级",
  COMPLETED: "已完成",
  FAILED: "失败",
  NEW: "待发布",
  SENT: "已发送",
  READ: "已读",
  UNREAD: "未读",
  ACTIVE: "已激活",
  INACTIVE: "已停用",
  VALIDATED: "已验证",
  CANARY: "灰度中",
  REJECTED: "已拒绝",
  ROLLED_BACK: "已回滚",
  ASSIGNED: "已分配",
  LEARNING_REQUIRED: "待完成培训",
  EXAM_REQUIRED: "待考试",
  RESTORE_PENDING: "Flowable 正在恢复权限",
  RESTORED: "权限已恢复",
  PASS: "通过",
  PROCESSING: "处理中",
  PARSING: "解析中",
  READY: "待处理",
  SUCCEEDED: "成功",
  PARTIAL_SUCCESS: "部分成功",
  PENDING_UPLOAD: "待上传",
  CHECKSUM_FAILED: "校验失败",
  RETRY: "重试中",
  DEGRADED: "降级运行",
  PUBLISHED: "已发布",
  CANCELLED: "已取消",
  FROZEN: "已冻结",
};

const labels: Record<string, string> = {
  POSITIVE: "阳性",
  NEGATIVE: "阴性",
  INDETERMINATE: "可疑",
  SUSPICIOUS: "可疑",
  INVALID: "无效",
};

const consistencies: Record<string, string> = {
  ALL_AGREE: "三方一致",
  TWO_AGREE_ONE_DIFF: "两方一致、一方不同",
  ALL_DIFFERENT: "三方均不一致",
  UNCERTAIN: "存在可疑或无效判读",
};

const reasons: Record<string, string> = {
  AI_DISSENT: "AI 判读与其余两方不一致",
  PRIMARY_DISSENT: "一级人工判读与其余两方不一致",
  SYSTEM_DISSENT: "系统判读与其余两方不一致",
  ALL_THREE_DIFFERENT: "系统、一级人工与 AI 三方判读均不同",
  UNCERTAIN_OR_INVALID: "存在可疑、无效或缺失判读",
  LOW_AI_CONFIDENCE: "AI 判读置信度较低",
  INTERNAL_CONTROL_FAILED: "内参质控未通过",
  POSSIBLE_FALSE_NEGATIVE: "关键靶标可能存在假阴性",
  CROSS_CHANNEL_INTERFERENCE: "可能存在通道间干扰",
  COLD_START_WEAK_LABEL_LOGISTIC: "冷启动弱标签模型判读",
  QUANTITATIVE_EVIDENCE_USED: "已结合 Ct、浓度和风险证据",
  LEGACY_CURVE_ONLY_MODEL: "旧模型仅使用曲线证据",
  LOW_MODEL_CONFIDENCE: "模型置信度低于复核阈值",
  STRUCTURED_WEAK_LABEL_FUSION: "已融合历史 Ct 与浓度结构化弱标签模型",
  SIGNAL_QC_INVALID: "信号质量控制未通过",
  ONNX_CLASSIFIER: "ONNX 模型分类结果",
  NON_FINITE_OR_FLAT_CURVE: "原始曲线包含无效值或无明显变化",
  SIGNIFICANT_RISE: "原始曲线振幅与上升斜率显著",
  BORDERLINE_AMPLITUDE: "原始曲线振幅处于临界区间",
  NO_SIGNIFICANT_RISE: "原始曲线未出现显著荧光上升",
};

const risks: Record<string, string> = {
  P1: "P1 紧急",
  P2: "P2 重要",
  P3: "P3 常规",
  HIGH: "高风险",
  WATCH: "关注",
  NORMAL: "正常",
  INSUFFICIENT_DATA: "数据不足",
};

const riskFlags: Record<string, string> = {
  BORDERLINE_CT: "临界 Ct",
  LOW_CONCENTRATION: "低浓度弱阳性",
};

const businessTypes: Record<string, string> = {
  FLUORESCENCE: "荧光曲线数据",
  FLUORESCENCE_RAW: "荧光曲线数据",
  POSITIVE_RATE_HISTORY: "历史阳性率数据",
};

const sources: Record<string, string> = {
  SYSTEM: "系统判读",
  INSTRUMENT: "系统判读",
  PRIMARY: "一级人工",
  HUMAN: "人工判读",
  AI: "AI 判读",
};

export const formatStatus = (value?: string | null) => value ? statuses[value] || value : "-";
export const formatLabel = (value?: string | null) => value ? labels[value] || value : "-";
export const formatConsistency = (value?: string | null) => value ? consistencies[value] || value : "-";
export const formatRisk = (value?: string | null) => value ? risks[value] || value : "-";
export const formatRiskRank = (value?: number | null) =>
  typeof value !== "number" ? "-" : value >= 4 ? "P1 紧急" : value >= 2 ? "P2 重要" : "P3 常规";
export const formatSource = (value?: string | null) => value ? sources[value] || value : "-";
export const formatBoolean = (value?: boolean | null) => value ? "是" : "否";
export const formatBusinessType = (value?: string | null) => value ? businessTypes[value] || value : "-";
export const labelTagType = (value?: string | null) => value === "POSITIVE" ? "danger" : value === "NEGATIVE" ? "success" : "warning";
export const formatCt = (value?: number | null) =>
  typeof value === "number" && Number.isFinite(value) ? value.toFixed(2) : "-";
export const formatRunWindow = (startedAt?: string | null, endedAt?: string | null) =>
  `${formatDateTime(startedAt)} 至 ${formatDateTime(endedAt)}`;
export const formatConcentration = (value?: number | null, unit?: string | null) =>
  typeof value === "number" && Number.isFinite(value)
    ? `${value.toLocaleString("zh-CN", { maximumFractionDigits: 2 })}${unit ? ` ${unit}` : ""}`
    : "-";
export const formatRiskFlags = (values?: string[] | null) => !values?.length ? "无" : values.map((value) => riskFlags[value] || value).join("；");
export function formatReasonCodes(values?: string[] | null) {
  if (!values?.length) return "无";
  return values.map((value) => reasons[value] || value).join("；");
}

type InferenceEvidence = {
  reasonCodes?: string[];
  reason_codes?: string[];
  inferenceLogic?: string;
  inference_logic?: string;
  features?: {
    baseline?: number;
    peak?: number;
    amplitude?: number;
    slope?: number;
    maxSlope?: number;
    peakIndex?: number;
    peak_index?: number;
    ctDetected?: number;
    ct_detected?: number;
    ctValue?: number;
    ct_value?: number;
    concentrationPresent?: number;
    concentration_present?: number;
    logConcentration?: number;
    log_concentration?: number;
    riskScore?: number;
    risk_score?: number;
  };
};

function formatFeature(value?: number) {
  return typeof value === "number" && Number.isFinite(value) ? value.toFixed(4) : "-";
}

export function formatInferenceEvidence(value?: string | null) {
  if (!value) return "AI 暂无可用推理证据";
  try {
    const evidence = JSON.parse(value) as InferenceEvidence;
    const features = evidence.features;
    const reasonCodes = evidence.reasonCodes || evidence.reason_codes;
    const inferenceLogic = evidence.inferenceLogic || evidence.inference_logic;
    const reasonText = formatReasonCodes(reasonCodes);
    if (!features) return `判读依据：${reasonText}${inferenceLogic ? `。推理逻辑：${inferenceLogic}` : ""}`;
    const peakIndex = features.peakIndex ?? features.peak_index;
    const ctDetected = (features.ctDetected ?? features.ct_detected ?? 0) > 0;
    const ctValue = features.ctValue ?? features.ct_value;
    const concentrationPresent = (features.concentrationPresent ?? features.concentration_present ?? 0) > 0;
    const logConcentration = features.logConcentration ?? features.log_concentration;
    const riskScore = features.riskScore ?? features.risk_score;
    const quantitativeEvidence = ctDetected || concentrationPresent
      ? `；量化证据：Ct ${ctDetected ? formatFeature(ctValue) : "未检出"}，浓度${concentrationPresent ? `特征 ${formatFeature(logConcentration)}` : "未检出"}，风险${(riskScore ?? 0) > 0 ? "关注" : "正常"}`
      : "";
    return `判读依据：${reasonText}。基线 ${formatFeature(features.baseline)}，峰值 ${formatFeature(features.peak)}，振幅 ${formatFeature(features.amplitude)}，最大斜率 ${formatFeature(features.slope ?? features.maxSlope)}，峰值周期 ${peakIndex ?? "-"}${quantitativeEvidence}${inferenceLogic ? `。推理逻辑：${inferenceLogic}` : ""}`;
  } catch {
    return value;
  }
}
