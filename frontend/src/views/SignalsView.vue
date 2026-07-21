<script setup lang="ts">
import axios from "axios";
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { useRoute } from "vue-router";
import ChartPanel from "../components/ChartPanel.vue";
import { signalApi } from "../api/modules";
import type { SignalCurve } from "../types";
import { formatStatus } from "../utils/format";

const route = useRoute();
const rows = ref<SignalCurve[]>([]);
const selected = ref<SignalCurve>();
const loading = ref(false);
const filters = reactive({ runNo: "", chamber: "", channelCode: "", qcStatus: "" });

async function load() {
  loading.value = true;
  try {
    const summaries = (await signalApi.page({ ...filters, pageNo: 1, pageSize: 100 })).records;
    const runNumbers = [...new Set(summaries.map((item) => item.runNo))];
    rows.value = (await Promise.all(runNumbers.map(signalApi.curves)))
      .flat()
      .filter((curve) => !filters.chamber || curve.chamber === filters.chamber)
      .filter((curve) => !filters.channelCode || curve.channelCode.includes(filters.channelCode))
      .filter((curve) => !filters.qcStatus || curve.qcStatus === filters.qcStatus)
      .map((curve) => ({ ...curve, pointCount: curve.rawValues?.length || 0 }));
    selected.value = rows.value[0];
  } catch (error: unknown) {
    rows.value = [];
    selected.value = undefined;
    if (axios.isAxiosError(error) && error.response?.status === 429) {
      ElMessage.error("请求过于频繁，请稍后重试");
    } else {
      ElMessage.error("曲线数据加载失败，请确认信号服务和 MongoDB 状态");
    }
  } finally {
    loading.value = false;
  }
}
const option = computed(() => ({
  tooltip: { trigger: "axis" },
  legend: { bottom: 0, data: ["矫正前原始曲线", "系统矫正后曲线"] },
  grid: { left: 60, right: 24, top: 30, bottom: 58 },
  xAxis: { type: "category", name: "循环数", data: (selected.value?.rawValues ?? []).map((_, i) => i + 1) },
  yAxis: { type: "value", name: "荧光强度", scale: true },
  series: [
    { name: "矫正前原始曲线", type: "line", showSymbol: false, data: selected.value?.rawValues ?? [] },
    { name: "系统矫正后曲线", type: "line", showSymbol: false, data: selected.value?.correctedValues ?? [] },
  ],
}));
onMounted(() => {
  filters.runNo = typeof route.query.runNo === "string" ? route.query.runNo : "";
  load();
});
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="page-intro"><div><h1>信号曲线分析</h1><p>曲线数据实时读取自 MongoDB</p></div></div>
    <section class="panel filter-bar signal-filter-bar">
      <el-input v-model="filters.runNo" placeholder="检测批次（支持模糊查询）" clearable @keyup.enter="load" />
      <el-select v-model="filters.chamber" placeholder="全部腔室" clearable>
        <el-option label="A 腔室" value="A" /><el-option label="B 腔室" value="B" />
      </el-select>
      <el-input v-model="filters.channelCode" placeholder="通道，如 FAM" clearable @keyup.enter="load" />
      <el-select v-model="filters.qcStatus" placeholder="全部 QC 状态" clearable>
        <el-option label="通过" value="PASS" /><el-option label="无效" value="INVALID" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
    </section>
    <div class="grid signal-layout">
      <section class="panel sample-list">
        <el-empty v-if="!rows.length" description="暂无真实曲线数据" />
        <div v-for="row in rows" :key="row.id" :class="['sample-item', { active: selected?.id === row.id }]" @click="selected = row">
          <b>{{ row.runNo }} / {{ row.chamber }}-{{ row.channelCode }}</b>
          <span>{{ formatStatus(row.qcStatus) }} · {{ row.pointCount }} 点</span>
        </div>
      </section>
      <section class="panel signal-detail">
        <div class="panel-head"><div><h3>{{ selected ? `${selected.runNo} 扩增曲线` : "请选择曲线" }}</h3><p>{{ selected?.chamber }} 腔室 · {{ selected?.channelCode }} · {{ selected?.processingVersion }}</p></div></div>
        <ChartPanel :option="option" height="430px" />
        <div v-if="selected" class="feature-grid">
          <div v-for="(value, key) in selected.features" :key="key"><span>{{ key }}</span><b>{{ value }}</b></div>
        </div>
      </section>
    </div>
  </div>
</template>
