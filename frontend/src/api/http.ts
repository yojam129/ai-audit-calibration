import axios from "axios";
import type { ApiResponse } from "../types";

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "",
  timeout: 12000,
});

function normalizePage<T>(value: T): T {
  if (value && typeof value === "object") {
    const page = value as Record<string, unknown>;
    if (Array.isArray(page.list) && !Array.isArray(page.records)) {
      return { ...page, records: page.list } as T;
    }
  }
  return value;
}

http.interceptors.request.use((config) => {
  const token = localStorage.getItem("access_token");
  if (token) config.headers.Authorization = `Bearer ${token}`;
  config.headers["X-Trace-Id"] = crypto.randomUUID();
  return config;
});

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>;
    if (body && Object.prototype.hasOwnProperty.call(body, "data")) {
      if (Number(body.code) !== 0) {
        return Promise.reject(new Error(body.message || "请求失败"));
      }
      return normalizePage(body.data);
    }
    return normalizePage(response.data);
  },
  async (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem("access_token");
      localStorage.removeItem("refresh_token");
      if (location.pathname !== "/login") location.href = "/login";
    }
    return Promise.reject(error);
  },
);
