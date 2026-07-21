<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import ChartPanel from "../components/ChartPanel.vue";
import { reviewApi, sampleApi, signalApi } from "../api/modules";
import type { AiInferenceResult, GroundTruth, Review, ReviewSourceTarget, SampleDetail, SampleDetectionDetail, SampleTargetDetail, SignalCurve } from "../types";
import {
  formatConcentration,
  formatConsistency,
  formatCt,
  formatInferenceEvidence,
  formatLabel,
  formatReasonCodes,
  formatRisk,
  formatRiskFlags,
  formatRiskRank,
  formatRunWindow,
  formatStatus,
  labelTagType,
} from "../utils/format";
import { useAuthStore } from "../stores/auth";

type Verdict = ReviewSourceTarget & { label: string; reasonCode: string; remark: string };
type QcEvidence = { A_IRC_CT?: number; B_IRC_CT?: number; activeChambers?: string[]; logic?: string };

const auth = useAuthStore();
const rows = ref<Review[]>([]);
const loading = ref(false);
const evidenceLoading = ref(false);
const finalizing = ref(false);
const dialogVisible = ref(false);
const activeReview = ref<Review | null>(null);
const sampleDetail = ref<SampleDetail>();
const activeDetection = ref<SampleDetectionDetail>();
const sourceTargets = ref<ReviewSourceTarget[]>([]);
const verdicts = ref<Verdict[]>([]);
const curves = ref<SignalCurve[]>([]);
const aiResults = ref<AiInferenceResult[]>([]);
const selectedTarget = ref<ReviewSourceTarget>();
const selectedCurve = ref<SignalCurve>();
const highRisk = ref(false);
const electronicSignature = ref("");
const readOnly = ref(false);
const activeTruth = ref<GroundTruth>();

function parseSources(value?: string): ReviewSourceTarget[] {
  if (!value) return [];
  try { return JSON.parse(value) as ReviewSourceTarget[]; } catch { return []; }
}
function parseQcEvidence(value?: string): QcEvidence {
  if (!value) return {};
  try { return JSON.parse(value) as QcEvidence; } catch { return {}; }
}
function normalize(value?: string) { return (value || "").replace(".", "").toUpperCase(); }
function targetPath(value?: string) {
  const parts = (value || "").split(":", 2);
  return parts.length === 2 ? { chamber: parts[0], targetCode: parts[1] } : { chamber: undefined, targetCode: value || "" };
}
function targetEvidence(targetCode: string): SampleTargetDetail | undefined {
  const path = targetPath(targetCode);
  return activeDetection.value?.targets.find((item) =>
    normalize(item.targetCode) === normalize(path.targetCode)
    && (!path.chamber || item.chamber === path.chamber),
  );
}
function aiEvidence(targetCode: string): AiInferenceResult | undefined {
  const path = targetPath(targetCode);
  const evidence = targetEvidence(targetCode);
  return aiResults.value.find((item) =>
    normalize(item.targetCode) === normalize(path.targetCode)
    && (!path.chamber || item.chamber === path.chamber)
    && (!evidence?.chamber || item.chamber === evidence.chamber),
  );
}

async function load() {
  loading.value = true;
  try { rows.value = (await reviewApi.page({ current: 1, size: 100 })).records; }
  catch { rows.value = []; ElMessage.error("复核任务加载失败"); }
  finally { loading.value = false; }
}
async function claim(row: Review) {
  try { await reviewApi.claim(row.id, row.version); ElMessage.success("任务领取成功"); await load(); }
  catch { ElMessage.error("任务状态已变化，请刷新后重试"); }
}
function selectTarget(target?: ReviewSourceTarget) {
  selectedTarget.value = target;
  const evidence = target ? targetEvidence(target.targetCode) : undefined;
  const path = targetPath(target?.targetCode);
  selectedCurve.value = curves.value.find((curve) =>
    (!path.chamber || curve.chamber === path.chamber)
    && (!evidence?.chamber || curve.chamber === evidence.chamber)
    && (normalize(curve.targetCode) === normalize(path.targetCode)
      || normalize(curve.channelCode) === normalize(evidence?.channelCode)),
  );
}
async function openFinalize(row: Review) {
  dialogVisible.value = true;
  evidenceLoading.value = true;
  activeReview.value = null;
  sampleDetail.value = undefined;
  activeDetection.value = undefined;
  sourceTargets.value = [];
  verdicts.value = [];
  curves.value = [];
  aiResults.value = [];
  selectedTarget.value = undefined;
  selectedCurve.value = undefined;
  activeTruth.value = undefined;
  readOnly.value = row.status === "ARCHIVED";
  try {
    const review = await reviewApi.get(row.id);
    activeReview.value = review;
    sourceTargets.value = parseSources(review.sourceTargetsJson);
    verdicts.value = sourceTargets.value.map((item) => ({
      ...item,
      label: "",
      reasonCode: "无",
      remark: "",
    }));
    highRisk.value = review.priority === "P1";
    electronicSignature.value = "";
    const sample = await sampleApi.byBusinessId(review.sampleId);
    activeReview.value = { ...review, sampleNo: sample.sampleNo };
    sampleDetail.value = await sampleApi.detail(sample.id);
    activeDetection.value = sampleDetail.value.detections[0];
    if (activeDetection.value?.runNo) {
      [curves.value, aiResults.value] = await Promise.all([
        signalApi.curves(activeDetection.value.runNo),
        signalApi.aiResults(activeDetection.value.runNo),
      ]);
    }
    if (readOnly.value) {
      const truths = await reviewApi.truths({ current: 1, size: 100, sampleId: review.sampleId });
      activeTruth.value = truths.records.find((item) => item.taskId === review.id) || truths.records[0];
    }
    verdicts.value = verdicts.value.map((item) => {
      const truth = activeTruth.value?.targets.find((target) => target.targetCode === item.targetCode);
      return { ...item, label: truth?.truthLabel || "", reasonCode: truth?.reasonCode || "无", remark: truth?.remark || "" };
    });
    selectTarget(verdicts.value[0]);
  } catch {
    ElMessage.error("终审证据加载失败");
  } finally { evidenceLoading.value = false; }
}

const curveOption = computed(() => ({
  tooltip: { trigger: "axis" },
  legend: { bottom: 0, data: ["矫正前原始曲线", "系统矫正后曲线"] },
  grid: { left: 58, right: 18, top: 28, bottom: 54 },
  xAxis: { type: "category", name: "循环数", data: Array.from({ length: Math.max(selectedCurve.value?.rawValues?.length || 0, selectedCurve.value?.correctedValues?.length || 0) }, (_, index) => index + 1) },
  yAxis: { type: "value", name: "荧光强度", scale: true },
  series: [
    { name: "矫正前原始曲线", type: "line", showSymbol: false, data: selectedCurve.value?.rawValues || [] },
    { name: "系统矫正后曲线", type: "line", showSymbol: false, data: selectedCurve.value?.correctedValues || [] },
  ],
}));
const selectedAi = computed(() => selectedTarget.value ? aiEvidence(selectedTarget.value.targetCode) : undefined);

async function finalizeReview() {
  if (readOnly.value) return;
  if (!activeReview.value || verdicts.value.length === 0 || verdicts.value.some((item) => !item.label || !item.reasonCode.trim())) {
    ElMessage.warning("必须为全部靶标标注最终真值和判定原因"); return;
  }
  if (highRisk.value && !electronicSignature.value.trim()) {
    ElMessage.warning("P1 加急终审必须填写电子签名"); return;
  }
  finalizing.value = true;
  try {
    await reviewApi.finalize(activeReview.value.id, {
      reviewerId: localStorage.getItem("user_name") || "current",
      expectedVersion: activeReview.value.version,
      targets: verdicts.value.map((item) => ({
        targetCode: item.targetCode, label: item.label, reasonCode: item.reasonCode, remark: item.remark,
        systemLabel: item.systemLabel, primaryLabel: item.primaryLabel, aiLabel: item.aiLabel,
      })),
      highRisk: highRisk.value,
      electronicSignature: electronicSignature.value,
    });
    dialogVisible.value = false;
    ElMessage.success("终审完成，最终标签已成为样本唯一标准真值");
    await auth.refreshMandatoryReview();
    await load();
  } catch { ElMessage.error("终审提交失败，请检查任务状态与靶标标签"); }
  finally { finalizing.value = false; }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div class="page-intro"><div><h1>复核工作台</h1><p>二级审核结合三方判读、定量证据和曲线，逐靶标确认唯一标准真值</p></div></div>
    <section class="panel">
      <el-table v-loading="loading" :data="rows" empty-text="暂无复核任务">
        <el-table-column prop="id" label="任务编号" min-width="210" show-overflow-tooltip />
        <el-table-column prop="sampleNo" label="样本编号" min-width="220" />
        <el-table-column label="优先级" width="120"><template #default="{ row }">{{ formatRisk(row.priority) }}</template></el-table-column>
        <el-table-column label="一致性结论" min-width="180"><template #default="{ row }">{{ formatConsistency(row.consistency) }}</template></el-table-column>
        <el-table-column label="状态" width="120"><template #default="{ row }">{{ formatStatus(row.status) }}</template></el-table-column>
        <el-table-column prop="ownerId" label="审核员" width="140" />
        <el-table-column label="操作" width="180"><template #default="{ row }">
          <el-button v-if="row.status === 'PENDING' || row.status === 'ESCALATED'" type="primary" link @click="claim(row)">领取任务</el-button>
          <el-button v-if="row.status === 'IN_REVIEW'" type="primary" link @click="openFinalize(row)">终审判定</el-button>
          <el-button v-if="row.status === 'ARCHIVED'" type="primary" link @click="openFinalize(row)">查看详情</el-button>
        </template></el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="readOnly ? '二级终审详情' : '二级终审判定'" width="96%" top="3vh">
      <div v-loading="evidenceLoading">
        <el-alert :title="highRisk ? 'P1 加急终审：必须完成全部靶标终审并签名后，才能进行其他操作。' : '最终标签将成为样本唯一标准真值，并反向标记系统、一级人工和 AI 是否准确。'" type="warning" :closable="false" show-icon />
        <el-descriptions v-if="activeReview" :column="4" border style="margin-top: 14px">
          <el-descriptions-item label="样本编号">{{ activeReview.sampleNo }}</el-descriptions-item>
          <el-descriptions-item label="仪器类型">{{ activeDetection?.instrumentType || "-" }}</el-descriptions-item>
          <el-descriptions-item label="检测时间">{{ formatRunWindow(activeDetection?.startedAt, activeDetection?.endedAt) }}</el-descriptions-item>
          <el-descriptions-item label="运行编号">{{ activeDetection?.runNo || "-" }}</el-descriptions-item>
          <el-descriptions-item label="IRC 质控" :span="4">
            A IRC Ct：{{ formatCt(parseQcEvidence(activeDetection?.qcEvidenceJson).A_IRC_CT) }}；
            B IRC Ct：{{ formatCt(parseQcEvidence(activeDetection?.qcEvidenceJson).B_IRC_CT) }}；
            {{ parseQcEvidence(activeDetection?.qcEvidenceJson).logic || "暂无质控逻辑" }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="review-workspace" style="margin-top: 16px">
          <section class="final-verdict-pane">
            <div class="final-verdict-head">
              <div><h3>逐靶标终审判定</h3><p>对照三方结果，为每个靶标标注唯一最终真值</p></div>
              <el-tag :type="readOnly ? 'info' : 'warning'">{{ readOnly ? "只读归档" : `${verdicts.length} 个靶标待确认` }}</el-tag>
            </div>
          <el-table :data="verdicts" row-key="targetCode" height="calc(100vh - 390px)" highlight-current-row empty-text="暂无靶标判读数据" @current-change="selectTarget">
            <el-table-column label="靶标" width="78"><template #default="{ row }">{{ targetPath(row.targetCode).targetCode }}</template></el-table-column>
            <el-table-column label="Ct" width="70"><template #default="{ row }">{{ formatCt(targetEvidence(row.targetCode)?.ctValue) }}</template></el-table-column>
            <el-table-column label="系统" width="74"><template #default="{ row }"><el-tag size="small" :type="labelTagType(row.systemLabel)">{{ formatLabel(row.systemLabel) }}</el-tag></template></el-table-column>
            <el-table-column label="一级" width="74"><template #default="{ row }"><el-tag size="small" :type="labelTagType(row.primaryLabel)">{{ formatLabel(row.primaryLabel) }}</el-tag></template></el-table-column>
            <el-table-column label="AI" width="120"><template #default="{ row }"><el-tag size="small" :type="labelTagType(row.aiLabel)">{{ formatLabel(row.aiLabel) }}</el-tag><small v-if="aiEvidence(row.targetCode)?.confidence != null"> {{ ((aiEvidence(row.targetCode)?.confidence || 0) * 100).toFixed(1) }}%</small></template></el-table-column>
            <el-table-column label="最终真值" width="128"><template #default="{ row: verdict }"><el-select v-model="verdict.label" :disabled="readOnly" placeholder="必选"><el-option label="阳性" value="POSITIVE" /><el-option label="阴性" value="NEGATIVE" /><el-option label="可疑" value="INDETERMINATE" /><el-option label="无效" value="INVALID" /></el-select></template></el-table-column>
            <el-table-column label="判定原因" min-width="135"><template #default="{ row: verdict }"><el-input v-model="verdict.reasonCode" :disabled="readOnly" /></template></el-table-column>
          </el-table>
          </section>
          <aside class="review-curve-pane">
            <div class="panel-head"><div><h3>曲线与 AI 推理</h3><p v-if="selectedTarget">{{ targetPath(selectedTarget.targetCode).targetCode }} · {{ selectedCurve?.chamber || targetPath(selectedTarget.targetCode).chamber || "-" }} 腔 · {{ selectedCurve?.channelCode || "-" }}</p></div></div>
            <div v-if="selectedAi" class="ai-evidence-panel">
              <div><el-tag :type="labelTagType(selectedAi.judgement)">{{ formatLabel(selectedAi.judgement) }}</el-tag><b>置信度 {{ ((selectedAi.confidence || 0) * 100).toFixed(1) }}%</b><span>模型 {{ selectedAi.modelVersion || "-" }}</span></div>
              <p v-if="selectedTarget">Ct {{ formatCt(targetEvidence(selectedTarget.targetCode)?.ctValue) }}；浓度 {{ formatConcentration(targetEvidence(selectedTarget.targetCode)?.concentrationValue, targetEvidence(selectedTarget.targetCode)?.concentrationUnit) }}；风险 {{ formatRisk(targetEvidence(selectedTarget.targetCode)?.riskLevel) }}（{{ formatRiskFlags(targetEvidence(selectedTarget.targetCode)?.riskFlags) }}）</p>
              <p v-if="selectedTarget">一致性：{{ formatConsistency(selectedTarget.consistency) }} · {{ formatRiskRank(selectedTarget.riskRank) }}；原因：{{ formatReasonCodes(selectedTarget.reasonCodes) }}</p>
              <p>{{ formatInferenceEvidence(selectedAi.evidenceJson) }}</p>
            </div>
            <ChartPanel v-if="selectedCurve" :option="curveOption" height="430px" />
            <el-empty v-else description="该靶标暂无曲线数据" />
          </aside>
        </div>
        <el-checkbox v-model="highRisk" :disabled="readOnly || activeReview?.priority === 'P1'" style="margin-top: 16px">高风险终审</el-checkbox>
        <el-input v-if="highRisk && !readOnly" v-model="electronicSignature" placeholder="电子签名" style="margin-top: 12px" />
      </div>
      <template #footer><el-button @click="dialogVisible = false">{{ readOnly ? "关闭" : "取消" }}</el-button><el-button v-if="!readOnly" type="primary" :loading="finalizing" @click="finalizeReview">确认终审</el-button></template>
    </el-dialog>
  </div>
</template>
