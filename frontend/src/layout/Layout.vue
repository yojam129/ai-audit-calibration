<script setup lang="ts">
import {
  Bell,
  DataAnalysis,
  DocumentChecked,
  Files,
  Histogram,
  Monitor,
  Opportunity,
  Setting,
  TrendCharts,
  Warning,
} from "@element-plus/icons-vue";
import { useRoute, useRouter } from "vue-router";
import { computed, onMounted, ref } from "vue";
import { useAuthStore } from "../stores/auth";
import { notificationApi } from "../api/modules";
const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const unreadNotifications = ref(0);
const menus = [
  ["/dashboard", "总体看板", Monitor, "dashboard:view"],
  ["/samples", "样本管理", Files, "sample:read"],
  ["/signals", "信号曲线", TrendCharts, "sample:read"],
  ["/primary-reviews", "一级审核工作台", DocumentChecked, "judgement:submit"],
  ["/comparisons", "判读一致性", DataAnalysis, "statistics:view"],
  ["/alerts", "预警中心", Warning, "alert:handle"],
  ["/reviews", "复核工作台", DocumentChecked, "review:handle"],
  ["/statistics", "统计分析", Histogram, "statistics:view"],
  ["/risk-learning", "风控与培训", Opportunity, ["risk:manage", "learning:participate"]],
  ["/operations", "运营管理", Setting, "operations:view"],
] as const;
const visibleMenus = computed(() =>
  menus.filter((item) => {
    if (auth.isPrimaryReviewer)
      return item[0] === "/primary-reviews" || item[0] === "/risk-learning";
    if (auth.isSecondaryReviewer && auth.mandatoryReviewRequired) return item[0] === "/reviews";
    const required = item[3];
    if (!required) return true;
    return typeof required === "string"
      ? auth.hasPermission(required)
      : required.some((permission) => auth.hasPermission(permission));
  }),
);
function logout() {
  auth.logout();
  router.push("/login");
}
async function loadUnreadNotifications() {
  if (auth.isPrimaryReviewer || auth.mandatoryReviewRequired) {
    unreadNotifications.value = 0;
    return;
  }
  try {
    unreadNotifications.value = await notificationApi.unread(
      localStorage.getItem("auth_user_id") || "current",
    );
  } catch {
    unreadNotifications.value = 0;
  }
}
function openNotifications() {
  if (auth.mandatoryReviewRequired) return;
  router.push({ path: "/operations", query: { tab: "notifications" } });
}
const roleName = computed(() => {
  if (auth.isPrimaryReviewer) return "一级审核员";
  if (auth.isSecondaryReviewer) return "二级审核员";
  return "平台管理员";
});
onMounted(async () => {
  await auth.refreshMandatoryReview();
  await loadUnreadNotifications();
});
</script>
<template>
  <el-container class="shell">
    <el-aside width="232px" class="aside">
      <div class="brand">
        <span class="brand-mark">AI</span>
        <div><b>智能审核管控</b><small>一致性校准平台</small></div>
      </div>
      <el-menu :default-active="route.path" router class="menu">
        <el-menu-item
          v-for="[path, label, icon] in visibleMenus"
          :key="path"
          :index="path"
          ><el-icon><component :is="icon" /></el-icon
          ><span>{{ label }}</span></el-menu-item
        >
      </el-menu>
      <div class="aside-foot"><span class="online-dot" />系统运行正常</div>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div>
          <h2>{{ route.meta.title }}</h2>
          <span class="date">2026年7月19日 · 全域实验室</span>
        </div>
        <div class="header-actions">
          <el-badge v-if="!auth.isPrimaryReviewer" :value="unreadNotifications" :hidden="!unreadNotifications"
            ><el-button circle :icon="Bell" :disabled="auth.mandatoryReviewRequired" @click="openNotifications" /></el-badge
          ><span class="avatar">管</span>
          <div>
            <b>{{ auth.userName || "系统管理员" }}</b
            ><small>{{ roleName }}</small>
          </div>
          <el-button link @click="logout">退出</el-button>
        </div>
      </el-header>
      <el-main><router-view /></el-main>
    </el-container>
  </el-container>
</template>
