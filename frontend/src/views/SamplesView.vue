<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { sampleApi } from "../api/modules";
import type { Sample, SampleDetail } from "../types";
import { formatConcentration, formatCt, formatDateTime, formatLabel, formatRisk, formatRiskFlags, formatStatus, labelTagType } from "../utils/format";

const filters = reactive({ sampleNo: "", organizationId: "", status: "", pageNo: 1, pageSize: 20 });
const rows = ref<Sample[]>([]);
const total = ref(0);
const loading = ref(false);
const detailLoading = ref(false);
const drawerVisible = ref(false);
const detail = ref<SampleDetail>();

type QcEvidence = {
  A_IRC_CT?: number;
  B_IRC_CT?: number;
  activeChambers?: string[];
  logic?: string;
};

function parseQcEvidence(value?: string): QcEvidence {
  if (!value) return {};
  try {
    return JSON.parse(value) as QcEvidence;
  } catch {
    return {};
  }
}

function formatIrcCt(value?: number) {
  return typeof value === "number" && Number.isFinite(value) ? String(value) : "-";
}

async function load() {
  loading.value = true;
  try {
    const page = await sampleApi.page(filters);
    rows.value = page.records;
    total.value = page.total;
  } catch {
    rows.value = [];
    total.value = 0;
    ElMessage.error("样本数据加载失败，请确认网关和样本服务已启动");
  } finally {
    loading.value = false;
  }
}
function search() {
  filters.pageNo = 1;
  load();
}
async function openDetail(row: Sample) {
  drawerVisible.value = true;
  detailLoading.value = true;
  try {
    detail.value = await sampleApi.detail(row.id);
  } catch {
    detail.value = undefined;
    ElMessage.error("样本详情加载失败");
  } finally {
    detailLoading.value = false;
  }
}
onMounted(load);
</script>

<template>
  <div class="page">
    <div class="page-intro">
      <div><h1>样本管理</h1><p>查询真实样本主数据、采集时间和处理状态</p></div>
    </div>
    <section class="panel filter-bar">
      <el-input v-model="filters.sampleNo" placeholder="样本编号（支持模糊查询）" clearable style="width: 260px" @keyup.enter="search" />
      <el-input v-model="filters.organizationId" placeholder="机构编号（支持模糊查询）" clearable style="width: 240px" @keyup.enter="search" />
      <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 160px">
        <el-option v-for="status in ['REGISTERED', 'DETECTED', 'CREATED', 'IMPORTED', 'COMPARISON_PENDING', 'ARCHIVED']" :key="status" :label="formatStatus(status)" :value="status" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
    </section>
    <section class="panel">
      <el-table v-loading="loading" :data="rows" empty-text="暂无真实样本数据" class="clickable-table" @row-click="openDetail">
        <el-table-column prop="sampleNo" label="样本编号" min-width="210" />
        <el-table-column prop="externalNo" label="外部单号" min-width="150" />
        <el-table-column prop="organizationId" label="机构编号" min-width="120" />
        <el-table-column prop="specimenType" label="样本类型" min-width="150" />
        <el-table-column label="状态" width="110"><template #default="{ row }">{{ formatStatus(row.status) }}</template></el-table-column>
        <el-table-column label="采集时间" width="180"><template #default="{ row }">{{ formatDateTime(row.collectedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="90"><template #default="{ row }"><el-button link type="primary" @click.stop="openDetail(row)">详情</el-button></template></el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="filters.pageNo" :page-size="filters.pageSize" :total="total" layout="total, prev, pager, next" @current-change="load" />
      </div>
    </section>

    <el-drawer v-model="drawerVisible" title="样本检测详情" size="920px">
      <div v-loading="detailLoading" class="sample-detail-drawer">
        <template v-if="detail">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="样本编号">{{ detail.sample.sampleNo }}</el-descriptions-item>
            <el-descriptions-item label="机构编号">{{ detail.sample.organizationId }}</el-descriptions-item>
            <el-descriptions-item label="外部单号">{{ detail.sample.externalNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="样本类型">{{ detail.sample.specimenType || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ formatStatus(detail.sample.status) }}</el-descriptions-item>
            <el-descriptions-item label="采集时间">{{ formatDateTime(detail.sample.collectedAt) }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-if="!detail.detections.length" description="暂无检测运行数据" />
          <el-collapse v-else accordion class="detection-collapse">
            <el-collapse-item v-for="detection in detail.detections" :key="`${detection.orderId}-${detection.runId}`" :title="`${detection.runNo || detection.orderNo} · ${detection.instrumentNo || '未知仪器'}`">
              <el-descriptions :column="2" size="small" border>
                <el-descriptions-item label="检测单">{{ detection.orderNo }}</el-descriptions-item>
                <el-descriptions-item label="检测项目">{{ detection.assayCode || '-' }}</el-descriptions-item>
                <el-descriptions-item label="检测面板">{{ detection.panelCode || '-' }}</el-descriptions-item>
                <el-descriptions-item label="仪器类型">{{ detection.instrumentType ?? '-' }}</el-descriptions-item>
                <el-descriptions-item label="模块位置">{{ detection.modulePosition ?? '-' }}</el-descriptions-item>
                <el-descriptions-item label="IRC 质控">
                  <div class="qc-evidence">
                    <el-tag :type="detection.qcStatus === 'PASS' ? 'success' : 'danger'">{{ formatStatus(detection.qcStatus) }}</el-tag>
                    <span>A IRC Ct：{{ formatIrcCt(parseQcEvidence(detection.qcEvidenceJson).A_IRC_CT) }}</span>
                    <span>B IRC Ct：{{ formatIrcCt(parseQcEvidence(detection.qcEvidenceJson).B_IRC_CT) }}</span>
                    <small>{{ parseQcEvidence(detection.qcEvidenceJson).logic || '暂无质控逻辑' }}</small>
                  </div>
                </el-descriptions-item>
                <el-descriptions-item label="卡盒/批次">{{ detection.cartridgeNo || '-' }} / {{ detection.reagentLotNo || '-' }}</el-descriptions-item>
                <el-descriptions-item label="开始时间">{{ formatDateTime(detection.startedAt) }}</el-descriptions-item>
                <el-descriptions-item label="结束时间">{{ formatDateTime(detection.endedAt) }}</el-descriptions-item>
              </el-descriptions>
              <el-table :data="detection.targets" size="small" max-height="320" class="target-detail-table">
                <el-table-column prop="chamber" label="腔室" width="70" />
                <el-table-column prop="targetCode" label="病原靶标" width="110" />
                <el-table-column prop="channelCode" label="通道" width="90" />
                <el-table-column label="Ct" width="90"><template #default="{ row }">{{ formatCt(row.ctValue) }}</template></el-table-column>
                <el-table-column label="浓度" min-width="170"><template #default="{ row }">{{ formatConcentration(row.concentrationValue, row.concentrationUnit) }}</template></el-table-column>
                <el-table-column label="风险" min-width="150"><template #default="{ row }"><el-tag v-if="row.riskLevel === 'WATCH'" type="warning">{{ formatRisk(row.riskLevel) }}</el-tag><span v-else>{{ formatRisk(row.riskLevel || 'NORMAL') }}</span><small v-if="row.riskFlags?.length"> {{ formatRiskFlags(row.riskFlags) }}</small></template></el-table-column>
                <el-table-column label="系统判读" width="120"><template #default="{ row }"><el-tag :type="labelTagType(row.systemJudgement)">{{ formatLabel(row.systemJudgement) }}</el-tag></template></el-table-column>
              </el-table>
            </el-collapse-item>
          </el-collapse>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.qc-evidence {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 12px;
}

.qc-evidence small {
  flex-basis: 100%;
  color: var(--el-text-color-secondary);
}
</style>
