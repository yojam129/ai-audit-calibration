<script setup lang="ts">
import * as echarts from "echarts";
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
const props = defineProps<{ option: unknown; height?: string }>();
const el = ref<HTMLElement>();
let chart: echarts.ECharts | undefined;
let observer: ResizeObserver | undefined;
const resize = () => chart?.resize();
onMounted(() => {
  if (el.value) {
    chart = echarts.init(el.value);
    chart.setOption(props.option as echarts.EChartsOption);
    observer = new ResizeObserver(() => chart?.resize());
    observer.observe(el.value);
    nextTick(resize);
    window.addEventListener("resize", resize);
  }
});
watch(
  () => props.option,
  (value) => chart?.setOption(value as echarts.EChartsOption, true),
  { deep: true },
);
onBeforeUnmount(() => {
  window.removeEventListener("resize", resize);
  observer?.disconnect();
  chart?.dispose();
});
</script>
<template>
  <div ref="el" :style="{ height: height || '320px', width: '100%' }" />
</template>
