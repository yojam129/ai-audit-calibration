<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { comparisonApi, sampleApi } from "../api/modules";
import type { Comparison, ComparisonDetail } from "../types";
import {
  formatConsistency,
  formatDateTime,
  formatLabel,
  formatReasonCodes,
  formatRiskRank,
  formatSource,
  labelTagType,
} from "../utils/format";

const rows = ref<Comparison[]>([]);
const total = ref(0);
const loading = ref(false);
const detailLoading = ref(false);
const dialogVisible = ref(false);
const detail = ref<ComparisonDetail | null>(null);
const query = reactive({ current: 1, size: 20, sampleId: "" });

async function resolveSampleNumbers(items: Comparison[]) {
  const unresolved = [...new Set(items.filter((item) => !item.sampleNo).map((item) => item.sampleId))];
  const resolved = await Promise.allSettled(
    unresolved.map(async (sampleId) => [sampleId, (await sampleApi.byBusinessId(sampleId)).sampleNo] as const),
  );
  const sampleNoById = new Map(
    resolved
      .filter((item): item is PromiseFulfilledResult<readonly [string, string]> => item.status === "fulfilled")
      .map((item) => item.value),
  );
  items.forEach((item) => { item.sampleNo ||= sampleNoById.get(item.sampleId); });
}

async function load() {
  loading.value = true;
  try {
    const params = { ...query, sampleId: query.sampleId || undefined };
    const page = await comparisonApi.page(params);
    rows.value = page.records;
    await resolveSampleNumbers(rows.value);
    total.value = Number(page.total || 0);
  } catch {
    rows.value = [];
    total.value = 0;
    ElMessage.error("判读一致性数据加载失败");
  } finally {
    loading.value = false;
  }
}

async function openDetail(row: Comparison) {
  dialogVisible.value = true;
  detailLoading.value = true;
  detail.value = null;
  try {
    detail.value = await comparisonApi.get(row.id);
    if (detail.value && !detail.value.sampleNo) {
      detail.value.sampleNo = (await sampleApi.byBusinessId(detail.value.sampleId)).sampleNo;
    }
  } catch {
    ElMessage.error("一致性详情加载失败");
  } finally {
    detailLoading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div class="page-intro">
      <div>
        <h1>判读一致性</h1>
        <p>逐靶标核对系统、一级人工与 AI 的判读差异和预警原因</p>
      </div>
    </div>
    <section class="panel filter-bar">
      <el-input v-model="query.sampleId" placeholder="样本业务 ID" clearable style="width: 280px" />
      <el-button type="primary" @click="query.current = 1; load()">查询</el-button>
    </section>
    <section class="panel">
      <el-table v-loading="loading" :data="rows" empty-text="暂无判读一致性数据" @row-click="openDetail">
        <el-table-column prop="id" label="比较编号" min-width="210" show-overflow-tooltip />
        <el-table-column prop="sampleNo" label="样本编号" min-width="210">
          <template #default="{ row }">{{ row.sampleNo || row.sampleId }}</template>
        </el-table-column>
        <el-table-column prop="comparisonVersion" label="版本" width="80" />
        <el-table-column label="一致性" min-width="180"><template #default="{ row }">{{ formatConsistency(row.consistency) }}</template></el-table-column>
        <el-table-column label="风险等级" width="120"><template #default="{ row }">{{ formatRiskRank(row.riskRank) }}</template></el-table-column>
        <el-table-column label="原因" min-width="260"><template #default="{ row }">{{ formatReasonCodes(row.reasonCodes) }}</template></el-table-column>
        <el-table-column label="比较时间" width="180"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="90"><template #default="{ row }"><el-button link type="primary" @click.stop="openDetail(row)">查看详情</el-button></template></el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
      </div>
    </section>

    <el-dialog v-model="dialogVisible" title="判读一致性详情" width="1080px">
      <div v-loading="detailLoading">
        <el-descriptions v-if="detail" :column="3" border>
          <el-descriptions-item label="样本编号">{{ detail.sampleNo || detail.sampleId }}</el-descriptions-item>
          <el-descriptions-item label="总体一致性">{{ formatConsistency(detail.consistency) }}</el-descriptions-item>
          <el-descriptions-item label="风险等级">{{ formatRiskRank(detail.riskRank) }}</el-descriptions-item>
          <el-descriptions-item label="比较编号" :span="2">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="比较时间">{{ formatDateTime(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="总体原因" :span="3">{{ formatReasonCodes(detail.reasonCodes) }}</el-descriptions-item>
        </el-descriptions>
        <el-table v-if="detail" :data="detail.targets" style="margin-top: 16px">
          <el-table-column prop="targetCode" label="靶标" width="120" />
          <el-table-column label="系统判读" width="120"><template #default="{ row }"><el-tag :type="labelTagType(row.systemLabel)">{{ formatLabel(row.systemLabel) }}</el-tag></template></el-table-column>
          <el-table-column label="一级判读" width="120"><template #default="{ row }"><el-tag :type="labelTagType(row.primaryLabel)">{{ formatLabel(row.primaryLabel) }}</el-tag></template></el-table-column>
          <el-table-column label="AI 判读" width="120"><template #default="{ row }"><el-tag :type="labelTagType(row.aiLabel)">{{ formatLabel(row.aiLabel) }}</el-tag></template></el-table-column>
          <el-table-column label="一致性" min-width="170"><template #default="{ row }">{{ formatConsistency(row.consistency) }}</template></el-table-column>
          <el-table-column label="差异方" width="120"><template #default="{ row }">{{ formatSource(row.dissentingSource) }}</template></el-table-column>
          <el-table-column label="风险" width="110"><template #default="{ row }">{{ formatRiskRank(row.riskRank) }}</template></el-table-column>
          <el-table-column label="原因" min-width="230"><template #default="{ row }">{{ formatReasonCodes(row.reasonCodes) }}</template></el-table-column>
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>
