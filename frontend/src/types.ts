export interface ApiResponse<T> {
  code: string | number;
  message: string;
  data: T;
  traceId?: string;
  timestamp?: string;
}
export interface PageVO<T> {
  records: T[];
  total: number;
  pageNo: number;
  pageSize: number;
  pages?: number;
}
export interface Sample {
  id: number;
  businessId?: string;
  sampleNo: string;
  organizationId: string;
  externalNo?: string;
  specimenType?: string;
  status: string;
  collectedAt?: string;
}
export interface SampleTargetDetail {
  chamber?: string;
  channelCode?: string;
  targetCode: string;
  systemJudgement?: string;
  ctValue?: number;
  concentrationValue?: number;
  concentrationUnit?: string;
  riskLevel?: string;
  riskFlags?: string[];
}
export interface SampleDetectionDetail {
  orderId: number;
  orderNo: string;
  assayCode?: string;
  orderStatus: string;
  runId?: number;
  runNo?: string;
  instrumentNo?: string;
  modulePosition?: string;
  panelCode?: string;
  instrumentType?: string;
  runStatus?: string;
  qcStatus?: string;
  qcEvidenceJson?: string;
  targetMappingJson?: string;
  overallResultJson?: string;
  startedAt?: string;
  endedAt?: string;
  cartridgeNo?: string;
  reagentLotNo?: string;
  targets: SampleTargetDetail[];
}
export interface SampleDetail {
  sample: Sample;
  detections: SampleDetectionDetail[];
}
export interface PrimaryReviewTarget {
  chamber: string;
  channelCode: string;
  targetCode: string;
  systemLabel?: string;
  ctValue?: number;
  concentrationValue?: number;
  concentrationUnit?: string;
  riskLevel?: string;
  riskFlags?: string[];
  aiLabel?: string;
  aiConfidence?: number;
  aiEvidenceJson?: string;
  aiStatus?: string;
}
export interface PrimaryReviewTask {
  id: number;
  sampleId: number;
  sampleBusinessId: string;
  sampleNo: string;
  runNo: string;
  startedAt?: string;
  endedAt?: string;
  status: string;
  reviewerName?: string;
  version: number;
  createdAt: string;
  targets: PrimaryReviewTarget[];
}
export interface Alert {
  id: string;
  level: string;
  sampleId: string;
  comparisonVersion: number;
  reasonCodes: string[];
  alertLogic?: string;
  ownerId?: string;
  status: string;
  slaDueAt?: string;
  version: number;
}
export interface PositiveRateAlert {
  id: number;
  organizationId: string;
  targetCode: string;
  windowStart: string;
  windowEnd: string;
  numerator: number;
  denominator: number;
  positiveRate: number;
  baselineNumerator: number;
  baselineDenominator: number;
  baselineRate: number;
  deviation: number;
  level: string;
  status: string;
  createdAt: string;
}
export interface Review {
  id: string;
  sampleId: string;
  sampleNo?: string;
  priority: string;
  consistency: string;
  ownerId?: string;
  status: string;
  processInstanceId?: string;
  sourceTargetsJson?: string;
  version: number;
}

export interface ReviewSourceTarget {
  targetCode: string;
  systemLabel?: string;
  primaryLabel?: string;
  aiLabel?: string;
  consistency?: string;
  dissentingSource?: string;
  riskRank?: number;
  reasonCodes?: string[];
}

export interface GroundTruthTarget {
  targetCode: string;
  truthLabel: string;
  reasonCode?: string;
  remark?: string;
  systemLabel?: string;
  primaryLabel?: string;
  aiLabel?: string;
  systemCorrect?: boolean | null;
  primaryCorrect?: boolean | null;
  aiCorrect?: boolean | null;
}

export interface GroundTruth {
  id: string;
  sampleId: string;
  sampleNo?: string;
  truthVersion: number;
  taskId: string;
  reviewerId: string;
  durationMs?: number;
  confirmedAt?: string;
  targets: GroundTruthTarget[];
}

export interface SignalCurve {
  id: string;
  runNo: string;
  chamber: string;
  channelCode: string;
  targetCode?: string;
  processingVersion: string;
  pointCount: number;
  qcStatus: string;
  features: Record<string, number>;
  checksum: string;
  rawValues?: number[];
  correctedValues?: number[];
}

export interface AiInferenceResult {
  id: number;
  curveId: string;
  runNo: string;
  chamber: string;
  targetCode: string;
  status: string;
  judgement?: string;
  confidence?: number;
  evidenceJson?: string;
  modelVersion?: string;
  failureReason?: string;
  updatedAt?: string;
}

export interface Comparison {
  id: string;
  sampleId: string;
  sampleNo?: string;
  comparisonVersion: number;
  consistency: string;
  riskLevel?: string;
  riskRank?: number;
  reasonCodes: string[];
  createdAt?: string;
}

export interface ComparisonTarget {
  targetCode: string;
  systemLabel?: string;
  primaryLabel?: string;
  aiLabel?: string;
  consistency?: string;
  dissentingSource?: string;
  riskRank?: number;
  reasonCodes?: string[];
}

export interface ComparisonDetail extends Comparison {
  targets: ComparisonTarget[];
}

export interface Accuracy {
  sourceType: string;
  correct: number;
  total: number;
  rate: number;
}

export interface Confusion {
  sourceType: string;
  targetCode: string;
  tp: number;
  tn: number;
  fp: number;
  fn: number;
  indeterminate: number;
  invalid: number;
  sensitivity: number;
  specificity: number;
}

export interface Dashboard {
  accuracy: Accuracy[];
  confusion: Confusion[];
  finalizedSamples: number;
}

export interface RiskProfile {
  reviewerId: string;
  windowStart: string;
  reviewed: number;
  correct: number;
  accuracy: number;
  recentReviewed: number;
  recentCorrect: number;
  recentAccuracy: number;
  recentWindowReady: boolean;
  averageDurationMs: number;
  level: string;
  trainingRequired: boolean;
  errorCounts?: Record<string, number>;
}

export interface RiskPolicy {
  qualificationAccuracyThreshold: number;
  updatedBy?: number;
  updatedAt?: string;
}

export interface ReviewerErrorFocus {
  id: number;
  reviewerId: string;
  sampleId?: string;
  sampleNo?: string;
  chamber?: string;
  channelCode?: string;
  targetCode: string;
  predictedLabel: string;
  truthLabel: string;
  errorType: string;
  occurredAt: string;
}

export interface LearningTask {
  id: number;
  assignmentId?: number;
  reviewerId: string;
  courseCode: string;
  errorType?: string;
  focusSampleId?: string;
  focusSampleNo?: string;
  focusChamber?: string;
  focusChannelCode?: string;
  focusTargetCode?: string;
  status: string;
  score?: number;
  deadline?: string;
  bestScore?: number;
  appliedAt?: string;
  processInstanceId?: string;
  workflowStartedAt?: string;
}
export interface ExamQuestion {
  id: number;
  stem: string;
  options: string[];
  score: number;
}
export interface Exam {
  attemptId: number;
  questions: ExamQuestion[];
}
export interface ImportBatch {
  id: number;
  batchNo: string;
  businessType: string;
  status: string;
  totalRows: number;
  successRows: number;
  errorRows: number;
  failureReason?: string;
}
export interface ImportError {
  rowNo: number;
  columnName?: string;
  errorCode: string;
  errorMessage: string;
}
export interface Presign {
  assetId: number;
  uploadUrl: string;
  bucketName: string;
  objectKey: string;
  expiresAt: string;
}
export interface Notification {
  id: number;
  requestId: string;
  userId: string;
  subject: string;
  status: string;
  read: boolean;
  createdAt: string;
  sentAt?: string;
}
export interface NotificationPreference {
  userId: string;
  email?: string;
  inAppEnabled: boolean;
  emailEnabled: boolean;
  eventTypes: string[];
}
export interface ModelVersion {
  id: number;
  modelCode: string;
  version: string;
  runtime: string;
  artifactUri: string;
  checksum: string;
  status: string;
  trafficPercent: number;
  createdAt?: string;
}
export interface TrendPoint {
  date: string;
  sourceType: string;
  correct: number;
  total: number;
  rate: number;
}

export interface InconsistencyDetail {
  sampleId: string;
  sampleNo?: string;
  truthVersion: number;
  targetCode: string;
  truthLabel: string;
  systemLabel?: string;
  primaryLabel?: string;
  aiLabel?: string;
  systemCorrect?: boolean | null;
  primaryCorrect?: boolean | null;
  aiCorrect?: boolean | null;
  occurredAt?: string;
}
