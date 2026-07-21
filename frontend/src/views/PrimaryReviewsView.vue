<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import ChartPanel from "../components/ChartPanel.vue";
import { primaryReviewApi, signalApi } from "../api/modules";
import { useAuthStore } from "../stores/auth";
import type { PrimaryReviewTarget, PrimaryReviewTask, SignalCurve } from "../types";
import { formatConcentration, formatCt, formatDateTime, formatInferenceEvidence, formatLabel, formatRisk, formatRiskFlags, formatRunWindow, formatStatus, labelTagType } from "../utils/format";

const auth = useAuthStore();
const rows = ref<PrimaryReviewTask[]>([]);
const active = ref<PrimaryReviewTask | null>(null);
const loading = ref(false);
const submitting = ref(false);
const dialogVisible = ref(false);
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);
const curveLoading = ref(false);
const reviewCurves = ref<SignalCurve[]>([]);
const selectedReviewCurve = ref<SignalCurve>();
const verdicts = ref<Array<{ chamber: string; targetCode: string; label: string; reasonCode: string; remark: string }>>([]);

const completed = computed(() => rows.value.filter((item) => item.status === "COMPARISON_COMPLETED").length);
const pending = computed(() => rows.value.filter((item) => item.status === "PENDING").length);

async function load() {
  loading.value = true;
  try {
    const page = await primaryReviewApi.page({ current: currentPage.value, size: pageSize.value });
    rows.value = page.records;
    total.value = page.total;
  } catch {
    ElMessage.error("一级审核任务加载失败");
  } finally {
    loading.value = false;
  }
}
function changePageSize() {
  currentPage.value = 1;
  load();
}
async function claim(row: PrimaryReviewTask) {
  try {
    await primaryReviewApi.claim(row.id, row.version);
    ElMessage.success("任务领取成功");
    await load();
  } catch {
    ElMessage.error("任务已被领取或状态发生变化");
  }
}
function normalizeChannel(value?: string) {
  return (value || "").replace(".", "").toUpperCase();
}
function selectTarget(target?: PrimaryReviewTarget) {
  if (!target) return;
  const channelCode = target.channelCode || target.targetCode;
  selectedReviewCurve.value = reviewCurves.value.find(
    (curve) => curve.chamber === target.chamber
      && normalizeChannel(curve.channelCode) === normalizeChannel(channelCode),
  );
}
async function openReview(row: PrimaryReviewTask) {
  try {
    active.value = await primaryReviewApi.get(row.id);
    verdicts.value = active.value.targets.map((target) => ({
      targetCode: target.targetCode,
      chamber: target.chamber,
      label: "",
      reasonCode: "无",
      remark: "",
    }));
    dialogVisible.value = true;
    curveLoading.value = true;
    try {
      reviewCurves.value = await signalApi.curves(active.value.runNo);
      selectTarget(active.value.targets[0]);
    } finally {
      curveLoading.value = false;
    }
  } catch {
    ElMessage.error("审核证据加载失败");
  }
}
const curveOption = computed(() => ({
  tooltip: { trigger: "axis" },
  legend: { bottom: 0, data: ["矫正前原始曲线", "系统矫正后曲线"] },
  grid: { left: 58, right: 18, top: 28, bottom: 54 },
  xAxis: { type: "category", name: "循环数", data: (selectedReviewCurve.value?.rawValues || []).map((_, i) => i + 1) },
  yAxis: { type: "value", name: "荧光强度", scale: true },
  series: [
    { name: "矫正前原始曲线", type: "line", showSymbol: false, data: selectedReviewCurve.value?.rawValues || [] },
    { name: "系统矫正后曲线", type: "line", showSymbol: false, data: selectedReviewCurve.value?.correctedValues || [] },
  ],
}));
async function submit() {
  if (!active.value || verdicts.value.some((item) => !item.label || !item.reasonCode)) {
    ElMessage.warning("请完成全部靶标判读并填写原因");
    return;
  }
  submitting.value = true;
  try {
    await primaryReviewApi.submit(active.value.id, { expectedVersion: active.value.version, targets: verdicts.value });
    dialogVisible.value = false;
    ElMessage.success("一级判读已提交，系统正在执行三方一致性比较");
    await load();
  } catch {
    ElMessage.error("一级判读提交失败");
  } finally {
    submitting.value = false;
  }
}
onMounted(load);
</script>

<template>
  <div class="page">
    <div class="page-intro"><div><h1>一级审核工作台</h1><p>查看检测证据并独立提交逐靶标人工判读</p></div></div>
    <div class="metric-grid">
      <article class="metric-card"><span>本页待领取</span><b>{{ pending }}</b><small>公共任务池</small></article>
      <article class="metric-card"><span>本页比较完成</span><b>{{ completed }}</b><small>已进入一致性流程</small></article>
      <article class="metric-card"><span>任务总数</span><b>{{ total }}</b><small>按页查看</small></article>
    </div>
    <section class="panel">
      <el-table v-loading="loading" :data="rows" empty-text="暂无一级审核任务">
        <el-table-column prop="sampleNo" label="样本号" min-width="210" />
        <el-table-column label="检测时间" min-width="310"><template #default="{ row }">{{ formatRunWindow(row.startedAt, row.endedAt) }}</template></el-table-column>
        <el-table-column label="状态" width="170"><template #default="{ row }">{{ formatStatus(row.status) }}</template></el-table-column>
        <el-table-column prop="reviewerName" label="审核员" width="150" />
        <el-table-column label="创建时间" width="180"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" type="primary" link @click="claim(row)">领取</el-button>
            <el-button v-if="row.status === 'IN_REVIEW'" type="primary" link @click="openReview(row)">开始审核</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="changePageSize" />
      </div>
    </section>

    <el-dialog v-model="dialogVisible" title="一级人工判读" width="96%" top="3vh" class="primary-review-dialog">
      <div class="review-context" v-if="active"><b>{{ active.sampleNo }}</b><span>检测时间 {{ formatRunWindow(active.startedAt, active.endedAt) }}</span><span>运行编号 {{ active.runNo }}</span></div>
      <el-alert title="请仅根据曲线、Ct、浓度与质控证据独立判读。" type="info" :closable="false" />
      <div class="review-workspace">
        <el-table :data="active?.targets || []" max-height="calc(100vh - 260px)" highlight-current-row @current-change="selectTarget">
          <el-table-column prop="chamber" label="反应仓" width="75" />
          <el-table-column prop="targetCode" label="病原靶标" width="90" />
          <el-table-column label="通道" width="78"><template #default="{ row }">{{ row.channelCode || row.targetCode }}</template></el-table-column>
          <el-table-column label="Ct" width="82"><template #default="{ row }">{{ formatCt(row.ctValue) }}</template></el-table-column>
          <el-table-column label="浓度/风险" min-width="185"><template #default="{ row }"><div>{{ formatConcentration(row.concentrationValue, row.concentrationUnit) }}</div><small>风险等级：{{ formatRisk(row.riskLevel || "NORMAL") }}</small><small v-if="row.riskFlags?.length"> · {{ formatRiskFlags(row.riskFlags) }}</small></template></el-table-column>
          <el-table-column v-if="!auth.isPrimaryReviewer" label="系统判读" width="100"><template #default="{ row }"><el-tag :type="labelTagType(row.systemLabel)">{{ formatLabel(row.systemLabel) }}</el-tag></template></el-table-column>
          <el-table-column v-if="!auth.isPrimaryReviewer" label="AI 证据" min-width="230">
            <template #default="{ row }"><div><el-tag size="small" :type="row.aiLabel ? labelTagType(row.aiLabel) : 'warning'">{{ row.aiLabel ? formatLabel(row.aiLabel) : formatStatus(row.aiStatus) }}</el-tag><span v-if="row.aiConfidence"> 置信度 {{ (row.aiConfidence * 100).toFixed(1) }}%</span></div><small>{{ formatInferenceEvidence(row.aiEvidenceJson) }}</small></template>
          </el-table-column>
          <el-table-column label="一级标签" width="140">
            <template #default="scope"><el-select v-model="verdicts[scope.$index].label" placeholder="必选"><el-option label="阳性" value="POSITIVE" /><el-option label="阴性" value="NEGATIVE" /><el-option label="可疑" value="INDETERMINATE" /><el-option label="无效" value="INVALID" /></el-select></template>
          </el-table-column>
          <el-table-column label="理由" width="150"><template #default="scope"><el-input v-model="verdicts[scope.$index].reasonCode" /></template></el-table-column>
        </el-table>
        <aside class="review-curve-pane" v-loading="curveLoading">
          <div class="panel-head"><div><h3>靶标曲线对照</h3><p v-if="selectedReviewCurve">{{ selectedReviewCurve.targetCode }} · {{ selectedReviewCurve.chamber }} 腔室 · {{ selectedReviewCurve.channelCode }}</p></div></div>
          <ChartPanel v-if="selectedReviewCurve" :option="curveOption" height="430px" />
          <el-empty v-else description="该靶标暂无曲线数据" />
        </aside>
      </div>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submit">提交一级判读</el-button></template>
    </el-dialog>
  </div>
</template>
