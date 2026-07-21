<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { alertApi, comparisonApi, sampleApi } from "../api/modules";
import type { Alert, ComparisonDetail, PositiveRateAlert, SampleDetail } from "../types";
import { formatConsistency, formatCt, formatDateTime, formatLabel, formatReasonCodes, formatRisk, formatRunWindow, formatStatus, labelTagType } from "../utils/format";

const rows = ref<Alert[]>([]);
const positiveRateRows = ref<PositiveRateAlert[]>([]);
const activeTab = ref("review");
const loading = ref(false);
const detailVisible = ref(false);
const detailLoading = ref(false);
const activeAlert = ref<Alert>();
const sampleDetail = ref<SampleDetail>();
const comparisonDetail = ref<ComparisonDetail>();
const alertStatusText: Record<string, string> = {
  OPEN: "待二级复核",
  CLAIMED: "复核处理中",
  ESCALATED: "加急复核",
  RESOLVED: "已归档",
};
const formatAlertStatus = (status?: string) =>
  (status && alertStatusText[status]) || formatStatus(status);
async function load() {
  loading.value = true;
  try {
    [rows.value, positiveRateRows.value] = await Promise.all([
      alertApi.list(),
      alertApi.positiveRates(),
    ]);
  } catch {
    rows.value = [];
    ElMessage.error("预警服务不可用");
  } finally {
    loading.value = false;
  }
}
async function openDetail(row: Alert) {
  activeAlert.value = row;
  detailVisible.value = true;
  detailLoading.value = true;
  sampleDetail.value = undefined;
  comparisonDetail.value = undefined;
  const [sampleResult, comparisonResult] = await Promise.allSettled([
    (async () => {
      const sample = await sampleApi.byBusinessId(row.sampleId);
      return sampleApi.detail(sample.id);
    })(),
    (async () => {
      const comparisons = await comparisonApi.page({ current: 1, size: 100, sampleId: row.sampleId });
      const summary = comparisons.records.find((item) => item.comparisonVersion === row.comparisonVersion);
      if (!summary) throw new Error("未找到对应的三方判读记录");
      return comparisonApi.get(summary.id);
    })(),
  ]);
  if (sampleResult.status === "fulfilled") sampleDetail.value = sampleResult.value;
  if (comparisonResult.status === "fulfilled") comparisonDetail.value = comparisonResult.value;
  if (sampleResult.status === "rejected" && comparisonResult.status === "rejected") {
    ElMessage.error("预警详情加载失败");
  } else if (sampleResult.status === "rejected" || comparisonResult.status === "rejected") {
    ElMessage.warning("部分关联数据暂时不可用，已展示可读取的预警信息");
  }
  detailLoading.value = false;
}
onMounted(load);
</script>

<template>
  <div class="page">
    <div class="page-intro">
      <div>
        <h1>预警中心</h1>
        <p>区分单样本三方判读预警与群体阳性率异常监测</p>
      </div>
    </div>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="审核预警" name="review">
    <section class="panel">
      <el-table v-loading="loading" :data="rows" empty-text="暂无预警">
        <el-table-column prop="id" label="预警编号" min-width="210" />
        <el-table-column prop="sampleId" label="样本 ID" min-width="210" />
        <el-table-column label="级别"
          ><template #default="{ row }"
            ><el-tag
              :type="
                row.level === 'P1'
                  ? 'danger'
                  : row.level === 'P2'
                    ? 'warning'
                    : 'info'
              "
              >{{ formatRisk(row.level) }}</el-tag
            ></template
          ></el-table-column
        >
        <el-table-column label="触发原因" min-width="220"
          ><template #default="{ row }">{{
            formatReasonCodes(row.reasonCodes)
          }}</template></el-table-column
        >
        <el-table-column label="状态"><template #default="{ row }">{{ formatAlertStatus(row.status) }}</template></el-table-column>
        <el-table-column label="SLA 截止时间" width="180"><template #default="{ row }">{{ formatDateTime(row.slaDueAt) }}</template></el-table-column>
        <el-table-column label="操作"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="openDetail(row)">详情</el-button></template
          ></el-table-column
        >
      </el-table>
    </section>
      </el-tab-pane>
      <el-tab-pane label="阳性率异常" name="positive-rate">
        <section class="panel">
          <el-table v-loading="loading" :data="positiveRateRows" empty-text="暂无阳性率异常预警">
            <el-table-column prop="targetCode" label="病原靶标" width="120" />
            <el-table-column prop="organizationId" label="机构" width="120" />
            <el-table-column label="当前窗口" min-width="230"><template #default="{ row }">{{ formatDateTime(row.windowStart) }} 至 {{ formatDateTime(row.windowEnd) }}</template></el-table-column>
            <el-table-column label="当前阳性率" width="140"><template #default="{ row }"><b>{{ (row.positiveRate * 100).toFixed(1) }}%</b>（{{ row.numerator }}/{{ row.denominator }}）</template></el-table-column>
            <el-table-column label="基线阳性率" width="140"><template #default="{ row }">{{ (row.baselineRate * 100).toFixed(1) }}%（{{ row.baselineNumerator }}/{{ row.baselineDenominator }}）</template></el-table-column>
            <el-table-column label="偏差" width="100"><template #default="{ row }"><el-tag type="danger">{{ (row.deviation * 100).toFixed(1) }}%</el-tag></template></el-table-column>
            <el-table-column label="级别" width="100"><template #default="{ row }">{{ formatRisk(row.level) }}</template></el-table-column>
            <el-table-column label="状态" width="100"><template #default="{ row }">{{ formatStatus(row.status) }}</template></el-table-column>
          </el-table>
        </section>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="detailVisible" title="预警详细信息" width="88%" top="5vh">
      <div v-loading="detailLoading">
        <el-descriptions v-if="activeAlert" :column="3" border>
          <el-descriptions-item label="预警编号">{{ activeAlert.id }}</el-descriptions-item>
          <el-descriptions-item label="样本编号">{{ sampleDetail?.sample.sampleNo || comparisonDetail?.sampleNo || activeAlert.sampleId }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ formatAlertStatus(activeAlert.status) }}</el-descriptions-item>
          <el-descriptions-item label="风险级别">{{ formatRisk(activeAlert.level) }}</el-descriptions-item>
          <el-descriptions-item label="SLA 截止">{{ formatDateTime(activeAlert.slaDueAt) }}</el-descriptions-item>
          <el-descriptions-item label="触发原因" :span="3">{{ formatReasonCodes(activeAlert.reasonCodes) }}</el-descriptions-item>
          <el-descriptions-item label="预警逻辑" :span="3">{{ activeAlert.alertLogic || "无" }}</el-descriptions-item>
        </el-descriptions>
        <el-descriptions v-if="sampleDetail?.detections[0]" :column="3" border style="margin-top: 14px">
          <el-descriptions-item label="运行编号">{{ sampleDetail.detections[0].runNo || "-" }}</el-descriptions-item>
          <el-descriptions-item label="仪器">{{ sampleDetail.detections[0].instrumentNo || "-" }} / {{ sampleDetail.detections[0].instrumentType || "-" }}</el-descriptions-item>
          <el-descriptions-item label="检测时间">{{ formatRunWindow(sampleDetail.detections[0].startedAt, sampleDetail.detections[0].endedAt) }}</el-descriptions-item>
        </el-descriptions>
        <section class="panel" style="margin-top: 14px">
          <div class="panel-head"><div><h3>逐靶标三方差异</h3><p>{{ formatConsistency(comparisonDetail?.consistency) }}</p></div></div>
          <el-table :data="comparisonDetail?.targets || []" empty-text="暂无三方比较详情">
            <el-table-column prop="targetCode" label="靶标" min-width="120" />
            <el-table-column label="Ct" width="100"><template #default="{ row }">{{ formatCt(sampleDetail?.detections[0]?.targets.find((item) => item.targetCode === row.targetCode)?.ctValue) }}</template></el-table-column>
            <el-table-column label="系统判读" width="120"><template #default="{ row }"><el-tag :type="labelTagType(row.systemLabel)">{{ formatLabel(row.systemLabel) }}</el-tag></template></el-table-column>
            <el-table-column label="一级判读" width="120"><template #default="{ row }"><el-tag :type="labelTagType(row.primaryLabel)">{{ formatLabel(row.primaryLabel) }}</el-tag></template></el-table-column>
            <el-table-column label="AI 判读" width="120"><template #default="{ row }"><el-tag :type="labelTagType(row.aiLabel)">{{ formatLabel(row.aiLabel) }}</el-tag></template></el-table-column>
            <el-table-column label="差异位置" min-width="190"><template #default="{ row }">{{ formatConsistency(row.consistency) }} · {{ formatReasonCodes(row.reasonCodes) }}</template></el-table-column>
          </el-table>
        </section>
      </div>
      <template #footer><el-button @click="detailVisible = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>
