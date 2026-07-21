import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { authApi, reviewApi } from "../api/modules";

type MandatoryReviewResponse =
  | boolean
  | null
  | undefined
  | unknown[]
  | {
      mandatory?: boolean;
      required?: boolean;
      hasMandatory?: boolean;
      pending?: boolean;
      exists?: boolean;
      total?: number;
      records?: unknown[];
      task?: unknown;
      review?: unknown;
      id?: string;
    };

function normalizeRole(role: string) {
  return role.replace(/^ROLE_/, "").toUpperCase();
}

function requiresMandatoryReview(result: MandatoryReviewResponse) {
  if (typeof result === "boolean") return result;
  if (Array.isArray(result)) return result.length > 0;
  if (!result) return false;
  if (typeof result.mandatory === "boolean") return result.mandatory;
  if (typeof result.required === "boolean") return result.required;
  if (typeof result.hasMandatory === "boolean") return result.hasMandatory;
  if (typeof result.pending === "boolean") return result.pending;
  if (typeof result.exists === "boolean") return result.exists;
  if (typeof result.total === "number") return result.total > 0;
  if (Array.isArray(result.records)) return result.records.length > 0;
  return Boolean(result.task || result.review || result.id);
}

export const useAuthStore = defineStore("auth", () => {
  const accessToken = ref(localStorage.getItem("access_token") || "");
  const userName = ref(localStorage.getItem("user_name") || "");
  const loggedIn = computed(() => Boolean(accessToken.value));
  const permissions = ref<string[]>(
    JSON.parse(localStorage.getItem("permissions") || "[]"),
  );
  const roles = ref<string[]>(JSON.parse(localStorage.getItem("roles") || "[]"));
  const normalizedRoles = computed(() => roles.value.map(normalizeRole));
  const isPrimaryReviewer = computed(() => normalizedRoles.value.includes("PRIMARY_REVIEWER"));
  const isSecondaryReviewer = computed(() => normalizedRoles.value.includes("SECONDARY_REVIEWER"));
  const mandatoryReviewRequired = ref(false);
  const mandatoryReviewChecked = ref(false);
  const defaultRoute = computed(() => {
    if (isPrimaryReviewer.value) return "/primary-reviews";
    if (isSecondaryReviewer.value) return "/reviews";
    return "/dashboard";
  });

  async function login(username: string, password: string) {
    const result = (await authApi.login(username, password)) as {
      accessToken?: string;
      refreshToken?: string;
      tokens?: { accessToken: string; refreshToken: string };
      user?: {
        name?: string;
        displayName?: string;
        username?: string;
        permissions?: string[];
        roles?: string[];
      };
    };
    if (!result) {
      throw new Error("登录响应为空");
    }
    const tokenPair = result.tokens || result;
    if (!tokenPair.accessToken || !tokenPair.refreshToken) {
      throw new Error("登录响应缺少令牌信息");
    }
    accessToken.value = tokenPair.accessToken;
    userName.value =
      result.user?.displayName || result.user?.name || result.user?.username || username;
    localStorage.setItem("access_token", tokenPair.accessToken);
    localStorage.setItem("refresh_token", tokenPair.refreshToken);
    localStorage.setItem("user_name", userName.value);
    localStorage.setItem("auth_user_id", result.user?.username || username);
    try {
      const payload = JSON.parse(
        atob(
          tokenPair.accessToken
            .split(".")[1]
            .replace(/-/g, "+")
            .replace(/_/g, "/"),
        ),
      ) as { permissions?: string[]; roles?: string[] };
      permissions.value = result.user?.permissions || payload.permissions || [];
      roles.value = result.user?.roles || payload.roles || [];
      localStorage.setItem("permissions", JSON.stringify(permissions.value));
      localStorage.setItem("roles", JSON.stringify(roles.value));
    } catch {
      permissions.value = [];
      roles.value = [];
      localStorage.setItem("permissions", "[]");
      localStorage.setItem("roles", "[]");
    }
  }

  async function refreshMandatoryReview() {
    if (!isSecondaryReviewer.value) {
      mandatoryReviewRequired.value = false;
      mandatoryReviewChecked.value = true;
      return false;
    }
    const mandatory = (reviewApi as typeof reviewApi & {
      mandatory?: () => Promise<MandatoryReviewResponse>;
    }).mandatory;
    if (typeof mandatory !== "function") {
      mandatoryReviewChecked.value = false;
      return false;
    }
    try {
      mandatoryReviewRequired.value = requiresMandatoryReview(await mandatory());
      mandatoryReviewChecked.value = true;
    } catch {
      mandatoryReviewRequired.value = true;
      mandatoryReviewChecked.value = true;
    }
    return mandatoryReviewRequired.value;
  }

  function logout() {
    accessToken.value = "";
    userName.value = "";
    permissions.value = [];
    roles.value = [];
    mandatoryReviewRequired.value = false;
    mandatoryReviewChecked.value = false;
    localStorage.clear();
  }

  const hasPermission = (permission: string) =>
    permissions.value.includes("*") || permissions.value.includes(permission);
  return {
    accessToken,
    userName,
    permissions,
    roles,
    isPrimaryReviewer,
    isSecondaryReviewer,
    mandatoryReviewRequired,
    mandatoryReviewChecked,
    defaultRoute,
    loggedIn,
    hasPermission,
    refreshMandatoryReview,
    login,
    logout,
  };
});
