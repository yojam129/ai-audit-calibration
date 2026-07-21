import { createRouter, createWebHistory } from "vue-router";
import Layout from "../layout/Layout.vue";
import { useAuthStore } from "../stores/auth";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/login",
      component: () => import("../views/LoginView.vue"),
      meta: { public: true },
    },
    {
      path: "/",
      component: Layout,
      redirect: () => useAuthStore().defaultRoute,
      children: [
        {
          path: "dashboard",
          component: () => import("../views/DashboardView.vue"),
          meta: { title: "总体看板", permission: "dashboard:view" },
        },
        {
          path: "samples",
          component: () => import("../views/SamplesView.vue"),
          meta: { title: "样本管理", permission: "sample:read" },
        },
        {
          path: "signals",
          component: () => import("../views/SignalsView.vue"),
          meta: { title: "信号曲线", permission: "sample:read" },
        },
        {
          path: "primary-reviews",
          component: () => import("../views/PrimaryReviewsView.vue"),
          meta: { title: "一级审核工作台", permission: "judgement:submit" },
        },
        {
          path: "comparisons",
          component: () => import("../views/ComparisonView.vue"),
          meta: { title: "判读一致性", permission: "statistics:view" },
        },
        {
          path: "alerts",
          component: () => import("../views/AlertsView.vue"),
          meta: { title: "预警中心", permission: "alert:handle" },
        },
        {
          path: "reviews",
          component: () => import("../views/ReviewsView.vue"),
          meta: { title: "复核工作台", permission: "review:handle" },
        },
        {
          path: "statistics",
          component: () => import("../views/StatisticsView.vue"),
          meta: { title: "统计分析", permission: "statistics:view" },
        },
        {
          path: "risk-learning",
          component: () => import("../views/RiskLearningView.vue"),
          meta: { title: "风控与培训", anyPermission: ["risk:manage", "learning:participate"] },
        },
        {
          path: "operations",
          component: () => import("../views/OperationsView.vue"),
          meta: { title: "运营管理", permission: "operations:view" },
        },
      ],
    },
  ],
});

router.beforeEach(async (to) => {
  if (!to.meta.public && !localStorage.getItem("access_token")) return "/login";
  const auth = useAuthStore();
  if (to.path === "/login" && localStorage.getItem("access_token")) return auth.defaultRoute;
  if (to.meta.public) return true;
  if (auth.isPrimaryReviewer && to.path !== "/primary-reviews") return "/primary-reviews";
  if (auth.isSecondaryReviewer) {
    const mandatory = await auth.refreshMandatoryReview();
    if (mandatory && to.path !== "/reviews") return "/reviews";
  }
  const required = to.meta.permission as string | undefined;
  const anyRequired = to.meta.anyPermission as string[] | undefined;
  if (required) {
    const permissions = JSON.parse(
      localStorage.getItem("permissions") || "[]",
    ) as string[];
    if (!permissions.includes(required) && !permissions.includes("*"))
      return auth.defaultRoute;
  }
  if (anyRequired) {
    const permissions = JSON.parse(localStorage.getItem("permissions") || "[]") as string[];
    if (!permissions.includes("*") && !anyRequired.some((item) => permissions.includes(item)))
      return auth.defaultRoute;
  }
});

export default router;
