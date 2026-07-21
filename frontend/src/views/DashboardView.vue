<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import ChartPanel from "../components/ChartPanel.vue";
import { alertApi, statisticsApi } from "../api/modules";
import { primaryReviewApi, reviewApi, learningApi } from "../api/modules";
import { useAuthStore } from "../stores/auth";
import { useRouter } from "vue-router";
import type { Alert, Dashboard } from "../types";
import { formatReasonCodes, formatRisk, formatSource, formatStatus } from "../utils/format";

const data = ref<Dashboard>({
  accuracy: [],
  confusion: [],
  finalizedSamples: 0,
});
const alerts = ref<Alert[]>([]);
const loading = ref(false);
const auth = useAuthStore();
const router = useRouter();
const primaryPending = ref(0);
const secondaryPending = ref(0);
const learningPending = ref(0);
const canStatistics = computed(() => auth.hasPermission("statistics:view"));
const canAlerts = computed(() => auth.hasPermission("alert:handle") || canStatistics.value);
const canPrimaryReview = computed(() => auth.hasPermission("judgement:submit"));
const canSecondaryReview = computed(() => auth.hasPermission("review:handle"));
const sourceOrder = ["SYSTEM", "PRIMARY", "AI"];
const allowedSources = new Set(sourceOrder);
const accuracy = computed(() => sourceOrder.map((sourceType) =>
  data.value.accuracy.find((item) => item.sourceType === sourceType)
  || { sourceType, correct: 0, total: 0, rate: 0 },
));
const confusion = computed(() => data.value.confusion.filter((item) => allowedSources.has(item.sourceType)));
onMounted(async () => {
  loading.value = true;
  const jobs: Promise<unknown>[] = [];
  if (canStatistics.value) jobs.push(statisticsApi.dashboard().then((value) => data.value = value));
  if (canAlerts.value) jobs.push(alertApi.list().then((value) => alerts.value = value));
  if (canPrimaryReview.value) jobs.push(primaryReviewApi.page({ current: 1, size: 100 }).then((value) => {
    primaryPending.value = value.records.filter((item) => item.status === "PENDING" || item.status === "IN_REVIEW").length;
  }));
  if (canSecondaryReview.value) jobs.push(reviewApi.page({ current: 1, size: 100 }).then((value) => {
    secondaryPending.value = value.records.filter((item) => item.status === "PENDING" || item.status === "IN_REVIEW").length;
  }));
  if (auth.hasPermission("learning:participate")) jobs.push(learningApi.page({ current: 1, size: 100 }).then((value) => {
    learningPending.value = value.records.filter((item) => item.status !== "RESTORED").length;
  }));
  const results = await Promise.allSettled(jobs);
  if (results.some((item) => item.status === "rejected")) ElMessage.warning("部分授权数据暂时不可用");
  loading.value = false;
});
const accuracyOption = computed(() => ({
  tooltip: { trigger: "axis" },
  xAxis: {
    type: "category",
    data: accuracy.value.map((x) => formatSource(x.sourceType)),
  },
  yAxis: {
    type: "value",
    min: 0,
    max: 100,
    axisLabel: { formatter: "{value}%" },
  },
  series: [
    {
      type: "bar",
      data: accuracy.value.map((x) => +(x.rate * 100).toFixed(2)),
      showBackground: true,
      label: { show: true, position: "top", formatter: "{c}%" },
    },
  ],
}));
const confusionOption = computed(() => ({
  tooltip: { trigger: "axis" },
  legend: { data: ["TP", "TN", "FP", "FN"] },
  xAxis: {
    type: "category",
    data: confusion.value.map((x) => `${formatSource(x.sourceType)}/${x.targetCode}`),
  },
  yAxis: { type: "value" },
  series: ["tp", "tn", "fp", "fn"].map((key) => ({
    name: key.toUpperCase(),
    type: "bar",
    stack: "confusion",
    data: confusion.value.map((x) => x[key as keyof typeof x] as number),
  })),
}));
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="page-intro">
      <div>
        <h1>{{ canStatistics ? "质量运营总览" : canPrimaryReview ? "我的一级审核" : "二级复核工作台" }}</h1>
        <p>{{ canStatistics ? "所有指标均来自二级终审真值事件" : "仅展示当前角色有权访问的任务与状态" }}</p>
      </div>
    </div>
    <div v-if="canPrimaryReview && !canStatistics" class="metric-grid">
      <article class="metric-card"><span>待审核任务</span><b>{{ primaryPending }}</b><small>待领取与审核中</small></article>
      <article class="metric-card"><span>待完成培训</span><b>{{ learningPending }}</b><small>课程、考试与权限状态</small></article>
      <article class="metric-card"><span>快捷入口</span><b>一级判读</b><el-button link type="primary" @click="router.push('/primary-reviews')">进入工作台</el-button></article>
    </div>
    <div v-if="canSecondaryReview && !canStatistics" class="metric-grid">
      <article class="metric-card"><span>待复核任务</span><b>{{ secondaryPending }}</b><small>预警推送与审核中</small></article>
      <article class="metric-card"><span>开放预警</span><b>{{ alerts.filter((x) => x.status === 'OPEN').length }}</b><small>按 SLA 优先处理</small></article>
      <article class="metric-card"><span>快捷入口</span><b>二级终审</b><el-button link type="primary" @click="router.push('/reviews')">进入工作台</el-button></article>
    </div>
    <div v-if="canStatistics" class="metric-grid">
      <article class="metric-card">
        <span>已终审样本</span><b>{{ data.finalizedSamples }}</b
        ><small>唯一真值口径</small>
      </article>
      <article
        v-for="item in accuracy"
        :key="item.sourceType"
        class="metric-card"
      >
        <span>{{ formatSource(item.sourceType) }}准确率</span
        ><b>{{ (item.rate * 100).toFixed(2) }}%</b>
        <small>正确 {{ item.correct }} / {{ item.total }}</small>
      </article>
      <article class="metric-card">
        <span>待处理预警</span
        ><b>{{ alerts.filter((x) => x.status === "OPEN").length }}</b
        ><small>实时预警服务</small>
      </article>
    </div>
    <div v-if="canStatistics" class="grid two">
      <section class="panel">
        <div class="panel-head"><h3>三方准确率</h3></div>
        <ChartPanel :option="accuracyOption" />
      </section>
      <section class="panel">
        <div class="panel-head"><h3>逐靶标混淆矩阵</h3></div>
        <ChartPanel :option="confusionOption" />
      </section>
    </div>
    <section v-if="canAlerts" class="panel">
      <div class="panel-head"><h3>最新风险预警</h3></div>
      <el-table :data="alerts.slice(0, 5)" empty-text="暂无预警">
        <el-table-column prop="id" label="预警编号" />
        <el-table-column prop="sampleId" label="样本 ID" />
        <el-table-column label="级别"><template #default="{ row }">{{ formatRisk(row.level) }}</template></el-table-column>
        <el-table-column label="原因"
          ><template #default="{ row }">{{
            formatReasonCodes(row.reasonCodes)
          }}</template></el-table-column
        >
        <el-table-column label="状态"><template #default="{ row }">{{ formatStatus(row.status) }}</template></el-table-column>
      </el-table>
    </section>
  </div>
</template>
