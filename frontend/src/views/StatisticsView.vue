<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import ChartPanel from "../components/ChartPanel.vue";
import { inconsistencyDetailApi, reviewApi, sampleApi, statisticsApi } from "../api/modules";
import type { Dashboard, GroundTruth, InconsistencyDetail, TrendPoint } from "../types";
import { formatDateTime, formatLabel, formatSource, labelTagType } from "../utils/format";

const SOURCE_ORDER = ["SYSTEM", "PRIMARY", "AI"];
const ALLOWED_SOURCES = new Set(SOURCE_ORDER);
const data = ref<Dashboard>({ accuracy: [], confusion: [], finalizedSamples: 0 });
const trend = ref<TrendPoint[]>([]);
const rows = ref<InconsistencyDetail[]>([]);
const truths = ref<GroundTruth[]>([]);
const total = ref(0);
const truthTotal = ref(0);
const page = ref(1);
const truthPage = ref(1);
const loading = ref(false);

const accuracy = computed(() => SOURCE_ORDER.map((sourceType) =>
  data.value.accuracy.find((item) => item.sourceType === sourceType)
  || { sourceType, correct: 0, total: 0, rate: 0 },
));
const filteredTrend = computed(() => trend.value.filter((item) => ALLOWED_SOURCES.has(item.sourceType)));

async function attachSampleNos(items: InconsistencyDetail[]) {
  const ids = [...new Set(items.filter((item) => !item.sampleNo).map((item) => item.sampleId))];
  const resolved = await Promise.allSettled(ids.map(async (id) => [id, (await sampleApi.byBusinessId(id)).sampleNo] as const));
  const map = new Map(resolved.flatMap((result) => result.status === "fulfilled" ? [result.value] : []));
  items.forEach((item) => { item.sampleNo ||= map.get(item.sampleId); });
}

async function load() {
  loading.value = true;
  try {
    const [dashboard, points, detail, truthPageResult] = await Promise.all([
      statisticsApi.dashboard(),
      statisticsApi.trend(30),
      inconsistencyDetailApi.page({ current: page.value, size: 20 }),
      reviewApi.truths({ current: truthPage.value, size: 10 }),
    ]);
    data.value = dashboard;
    trend.value = points as TrendPoint[];
    rows.value = detail.records;
    total.value = Number(detail.total || 0);
    truths.value = truthPageResult.records;
    truthTotal.value = Number(truthPageResult.total || 0);
    await attachSampleNos(rows.value);
  } catch { ElMessage.error("统计服务加载失败"); }
  finally { loading.value = false; }
}

const trendOption = computed(() => {
  const dates = [...new Set(filteredTrend.value.map((item) => item.date))];
  const sources = SOURCE_ORDER;
  return {
    tooltip: { trigger: "axis" },
    legend: { data: sources.map(formatSource) },
    xAxis: { type: "category", data: dates },
    yAxis: { type: "value", min: 0, max: 100, axisLabel: { formatter: "{value}%" } },
    series: sources.map((source) => ({
      name: formatSource(source), type: "line", smooth: true,
      data: dates.map((date) => +(((filteredTrend.value.find((item) => item.date === date && item.sourceType === source)?.rate || 0) * 100).toFixed(2))),
    })),
  };
});
function correctness(value?: boolean | null) { return value === true ? "正确" : value === false ? "错误" : "无判读"; }
function correctnessType(value?: boolean | null) { return value === true ? "success" : value === false ? "danger" : "info"; }

onMounted(load);
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="page-intro"><div><h1>质量统计</h1><p>以二级终审真值为唯一标准，计算系统、一级人工与 AI 的真实准确性</p></div></div>
    <div class="metric-grid">
      <article class="metric-card"><span>已终审样本</span><b>{{ data.finalizedSamples }}</b></article>
      <article v-for="item in accuracy" :key="item.sourceType" class="metric-card">
        <span>{{ formatSource(item.sourceType) }}准确率</span><b>{{ (item.rate * 100).toFixed(2) }}%</b><small>{{ item.correct }} / {{ item.total }}</small>
      </article>
    </div>
    <section class="panel"><div class="panel-head"><h3>近 30 日三方准确率趋势</h3></div><ChartPanel :option="trendOption" height="360px" /></section>

    <section class="panel">
      <div class="panel-head"><div><h3>不一致样本明细</h3><p>只列出至少一方与终审真值不同的靶标，明确显示具体错误方</p></div></div>
      <el-table :data="rows" empty-text="暂无不一致数据">
        <el-table-column label="样本编号" min-width="190"><template #default="{ row }">{{ row.sampleNo || row.sampleId }}</template></el-table-column>
        <el-table-column prop="targetCode" label="靶标" width="100" />
        <el-table-column label="最终真值" width="110"><template #default="{ row }"><el-tag :type="labelTagType(row.truthLabel)">{{ formatLabel(row.truthLabel) }}</el-tag></template></el-table-column>
        <el-table-column label="系统判读" width="140"><template #default="{ row }"><el-tag :type="correctnessType(row.systemCorrect)">{{ formatLabel(row.systemLabel) }} · {{ correctness(row.systemCorrect) }}</el-tag></template></el-table-column>
        <el-table-column label="一级判读" width="140"><template #default="{ row }"><el-tag :type="correctnessType(row.primaryCorrect)">{{ formatLabel(row.primaryLabel) }} · {{ correctness(row.primaryCorrect) }}</el-tag></template></el-table-column>
        <el-table-column label="AI 判读" width="140"><template #default="{ row }"><el-tag :type="correctnessType(row.aiCorrect)">{{ formatLabel(row.aiLabel) }} · {{ correctness(row.aiCorrect) }}</el-tag></template></el-table-column>
        <el-table-column label="终审时间" width="180"><template #default="{ row }">{{ formatDateTime(row.occurredAt) }}</template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" :total="total" :page-size="20" layout="total, prev, pager, next" @current-change="load" />
    </section>

    <section class="panel">
      <div class="panel-head"><div><h3>终审真值与反向准确性清单</h3><p>二级终审标签是唯一标准真值，二级本身不参与准确率计算</p></div></div>
      <el-table :data="truths" row-key="id" empty-text="暂无终审真值">
        <el-table-column type="expand"><template #default="{ row }">
          <el-table :data="row.targets">
            <el-table-column prop="targetCode" label="靶标" width="110" />
            <el-table-column label="最终真值" width="120"><template #default="scope"><el-tag :type="labelTagType(scope.row.truthLabel)">{{ formatLabel(scope.row.truthLabel) }}</el-tag></template></el-table-column>
            <el-table-column label="系统"><template #default="scope">{{ formatLabel(scope.row.systemLabel) }} · <el-tag :type="correctnessType(scope.row.systemCorrect)">{{ correctness(scope.row.systemCorrect) }}</el-tag></template></el-table-column>
            <el-table-column label="一级人工"><template #default="scope">{{ formatLabel(scope.row.primaryLabel) }} · <el-tag :type="correctnessType(scope.row.primaryCorrect)">{{ correctness(scope.row.primaryCorrect) }}</el-tag></template></el-table-column>
            <el-table-column label="AI"><template #default="scope">{{ formatLabel(scope.row.aiLabel) }} · <el-tag :type="correctnessType(scope.row.aiCorrect)">{{ correctness(scope.row.aiCorrect) }}</el-tag></template></el-table-column>
            <el-table-column prop="reasonCode" label="终审原因" min-width="160" />
            <el-table-column prop="remark" label="备注" min-width="150" />
          </el-table>
        </template></el-table-column>
        <el-table-column prop="sampleNo" label="样本编号" min-width="200" />
        <el-table-column prop="truthVersion" label="真值版本" width="100" />
        <el-table-column prop="reviewerId" label="二级审核员" width="140" />
        <el-table-column label="终审时间" width="180"><template #default="{ row }">{{ formatDateTime(row.confirmedAt) }}</template></el-table-column>
        <el-table-column label="靶标数" width="90"><template #default="{ row }">{{ row.targets.length }}</template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="truthPage" :total="truthTotal" :page-size="10" layout="total, prev, pager, next" @current-change="load" />
    </section>
  </div>
</template>
