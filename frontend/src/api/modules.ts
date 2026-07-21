import type {
  Alert,
  AiInferenceResult,
  Comparison,
  ComparisonDetail,
  Dashboard,
  LearningTask,
  PageVO,
  Review,
  RiskProfile,
  RiskPolicy,
  ReviewerErrorFocus,
  Sample,
  SampleDetail,
  SignalCurve,
  ImportBatch,
  ImportError,
  Presign,
  Notification,
  NotificationPreference,
  ModelVersion,
  TrendPoint,
  Confusion,
  Exam,
  PrimaryReviewTask,
  PositiveRateAlert,
  GroundTruth,
  InconsistencyDetail,
} from "../types";
import { http } from "./http";

export const authApi = {
  login: (username: string, password: string) =>
    http.post("/auth/login", { username, password }) as Promise<unknown>,
  refresh: (refreshToken: string) =>
    http.post("/auth/refresh", { refreshToken }) as Promise<unknown>,
  logout: (refreshToken: string) =>
    http.post("/auth/logout", { refreshToken }) as Promise<void>,
};
export const primaryReviewApi = {
  page: (params: Record<string, unknown> = {}) =>
    http.get("/api/v1/primary-reviews", { params }) as Promise<unknown> as Promise<PageVO<PrimaryReviewTask>>,
  get: (id: number) =>
    http.get(`/api/v1/primary-reviews/${id}`) as Promise<unknown> as Promise<PrimaryReviewTask>,
  claim: (id: number, expectedVersion: number) =>
    http.post(`/api/v1/primary-reviews/${id}/claim`, { expectedVersion }) as Promise<unknown> as Promise<PrimaryReviewTask>,
  submit: (id: number, payload: unknown) =>
    http.post(`/api/v1/primary-reviews/${id}/submit`, payload) as Promise<unknown> as Promise<PrimaryReviewTask>,
};

export const sampleApi = {
  page: (params: Record<string, unknown>) =>
    http.get("/api/samples", { params }) as Promise<unknown> as Promise<
      PageVO<Sample>
    >,
  get: (id: number) =>
    http.get(`/api/samples/${id}`) as Promise<unknown> as Promise<Sample>,
  detail: (id: number) =>
    http.get(`/api/samples/${id}/detail`) as Promise<unknown> as Promise<SampleDetail>,
  byBusinessId: (businessId: string) =>
    http.get(`/api/samples/business/${businessId}`) as Promise<unknown> as Promise<Sample>,
};

export const signalApi = {
  page: (params: Record<string, unknown>) =>
    http.get("/api/signals", { params }) as Promise<unknown> as Promise<
      PageVO<SignalCurve>
    >,
  bySample: (sampleId: number) =>
    http.get("/api/signals", {
      params: { sampleId, pageNo: 1, pageSize: 100 },
    }) as Promise<unknown> as Promise<PageVO<SignalCurve>>,
  curves: (runNo: string) =>
    http.get(`/api/signals/runs/${encodeURIComponent(runNo)}/curves`) as Promise<unknown> as Promise<SignalCurve[]>,
  aiResults: (runNo: string) =>
    http.get("/api/signals/ai-results", { params: { runNo } }) as Promise<unknown> as Promise<AiInferenceResult[]>,
};

export const comparisonApi = {
  page: (params: Record<string, unknown>) =>
    http.get("/api/v1/comparisons", { params }) as Promise<unknown> as Promise<
      PageVO<Comparison>
    >,
  get: (id: string) =>
    http.get(
      `/api/v1/comparisons/${id}`,
    ) as Promise<unknown> as Promise<ComparisonDetail>,
};

export const alertApi = {
  list: () =>
    http.get("/api/v1/alerts") as Promise<unknown> as Promise<Alert[]>,
  claim: (id: string, expectedVersion: number) =>
    http.post(`/api/v1/alerts/${id}/claim`, {
      ownerId: "current",
      expectedVersion,
    }) as Promise<unknown> as Promise<Alert>,
  positiveRates: () =>
    http.get("/api/v1/alerts/positive-rate") as Promise<unknown> as Promise<PositiveRateAlert[]>,
};

export const reviewApi = {
  mandatory: () =>
    http.get("/api/v1/reviews/mandatory") as Promise<unknown> as Promise<boolean>,
  page: (params: Record<string, unknown> = {}) =>
    http.get("/api/v1/reviews", { params }) as Promise<unknown> as Promise<
      PageVO<Review>
    >,
  get: (id: string) =>
    http.get(`/api/v1/reviews/${id}`) as Promise<unknown> as Promise<Review>,
  claim: (id: string, expectedVersion: number) =>
    http.post(`/api/v1/reviews/${id}/claim`, {
      reviewerId: "current",
      expectedVersion,
    }) as Promise<unknown> as Promise<Review>,
  finalize: (id: string, payload: unknown) =>
    http.post(`/api/v1/reviews/${id}/finalize`, payload),
  truths: (params: Record<string, unknown> = {}) =>
    http.get("/api/v1/reviews/truths", { params }) as Promise<unknown> as Promise<PageVO<GroundTruth>>,
};

export const statisticsApi = {
  dashboard: () =>
    http.get(
      "/api/v1/statistics/dashboard",
    ) as Promise<unknown> as Promise<Dashboard>,
  trend: (days = 7) =>
    http.get("/api/v1/statistics/trend", {
      params: { days },
    }) as Promise<unknown> as Promise<unknown[]>,
};

export const riskApi = {
  page: (params: Record<string, unknown> = {}) =>
    http.get("/api/v1/risks", { params }) as Promise<unknown> as Promise<
      PageVO<RiskProfile>
    >,
  errors: (params: Record<string, unknown> = {}) =>
    http.get("/api/v1/risks/errors", { params }) as Promise<unknown> as Promise<
      PageVO<ReviewerErrorFocus>
    >,
  myErrors: (params: Record<string, unknown> = {}) =>
    http.get("/api/v1/risks/me/errors", { params }) as Promise<unknown> as Promise<
      PageVO<ReviewerErrorFocus>
    >,
  policy: () =>
    http.get("/api/v1/risks/policy") as Promise<unknown> as Promise<RiskPolicy>,
  updatePolicy: (qualificationAccuracyThreshold: number) =>
    http.put("/api/v1/risks/policy", { qualificationAccuracyThreshold }) as Promise<unknown> as Promise<RiskPolicy>,
};

export const learningApi = {
  page: (params: Record<string, unknown> = {}) =>
    http.get("/api/v1/learning", { params }) as Promise<unknown> as Promise<
      PageVO<LearningTask>
    >,
  startExam: (id: number) =>
    http.post(
      `/api/v1/learning/${id}/exam/start`,
    ) as Promise<unknown> as Promise<Exam>,
  completeTraining: (id: number) =>
    http.post(`/api/v1/learning/${id}/training/complete`),
  submitExam: (
    id: number,
    payload: {
      attemptId: number;
      answers: { questionId: number; selectedOptions: string[] }[];
    },
  ) => http.post(`/api/v1/learning/${id}/exam`, payload),
};
export const integrationApi = {
  presign: (payload: {
    fileName: string;
    contentType: string;
    sizeBytes: number;
    sha256: string;
  }) =>
    http.post(
      "/api/integration/files/presign",
      payload,
    ) as Promise<unknown> as Promise<Presign>,
  confirm: (payload: { assetId: number; sizeBytes: number; sha256: string }) =>
    http.post("/api/integration/files/confirm", payload),
  createImport: (payload: {
    assetId: number;
    businessType: string;
    templateVersion: string;
  }) =>
    http.post(
      "/api/integration/imports",
      payload,
    ) as Promise<unknown> as Promise<ImportBatch>,
  page: (params: Record<string, unknown>) =>
    http.get("/api/integration/imports", {
      params,
    }) as Promise<unknown> as Promise<PageVO<ImportBatch>>,
  errors: (id: number) =>
    http.get(
      `/api/integration/imports/${id}/errors`,
    ) as Promise<unknown> as Promise<ImportError[]>,
};
export const notificationApi = {
  page: (params: Record<string, unknown>) =>
    http.get("/api/v1/notifications", {
      params,
    }) as Promise<unknown> as Promise<PageVO<Notification>>,
  unread: (userId: string) =>
    http.get("/api/v1/notifications/unread-count", {
      params: { userId },
    }) as Promise<unknown> as Promise<number>,
  read: (id: number) => http.post(`/api/v1/notifications/${id}/read`),
  preference: (userId: string) =>
    http.get(
      `/api/v1/notifications/preferences/${userId}`,
    ) as Promise<unknown> as Promise<NotificationPreference>,
  updatePreference: (
    userId: string,
    payload: Omit<NotificationPreference, "userId">,
  ) => http.put(`/api/v1/notifications/preferences/${userId}`, payload),
};
export const modelApi = {
  current: (modelCode: string) =>
    http.get("/api/models/current", {
      params: { modelCode },
    }) as Promise<unknown> as Promise<ModelVersion>,
  register: (payload: Record<string, unknown>) =>
    http.post(
      "/api/models",
      payload,
    ) as Promise<unknown> as Promise<ModelVersion>,
  deploy: (id: number, trafficPercent: number) =>
    http.post(`/api/models/${id}/deployment`, null, {
      params: { trafficPercent },
    }),
  rollback: (id: number, reason: string) =>
    http.post(`/api/models/${id}/rollback`, null, { params: { reason } }),
};
statisticsApi.trend = (days = 7) => {
  const to = new Date();
  const from = new Date(to.getTime() - (days - 1) * 86400000);
  return http.get("/api/v1/statistics/accuracy/trend", {
    params: {
      from: from.toISOString().slice(0, 10),
      to: to.toISOString().slice(0, 10),
    },
  }) as Promise<unknown> as Promise<TrendPoint[]>;
};
export const inconsistencyApi = {
  page: (params: Record<string, unknown>) =>
    http.get("/api/v1/statistics/inconsistencies", {
      params,
    }) as Promise<unknown> as Promise<PageVO<Confusion>>,
};

export const inconsistencyDetailApi = {
  page: (params: Record<string, unknown>) =>
    http.get("/api/v1/statistics/inconsistency-details", { params }) as Promise<unknown> as Promise<PageVO<InconsistencyDetail>>,
};
