<script setup lang="ts">
import { Lock, User } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "../stores/auth";
const form = reactive({ username: "admin", password: "Admin@123456" });
const loading = ref(false);
const router = useRouter();
const auth = useAuthStore();
async function submit() {
  loading.value = true;
  try {
    await auth.login(form.username, form.password);
    ElMessage.success("登录成功");
    router.push("/dashboard");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "登录失败，请稍后重试");
  } finally {
    loading.value = false;
  }
}
</script>
<template>
  <div class="login-page">
    <div class="login-visual">
      <div class="visual-copy">
        <span class="eyebrow">AI QUALITY GOVERNANCE</span>
        <h1>让每一次审核判读<br />都有标准可循</h1>
        <p>
          聚合仪器、人工与 AI
          三方判断，以可追溯的数据闭环持续提升实验室审核质量。
        </p>
        <div class="visual-stats">
          <div><b>96.8%</b><span>判读一致率</span></div>
          <div><b>24h</b><span>实时风险监控</span></div>
          <div><b>100%</b><span>全流程留痕</span></div>
        </div>
      </div>
    </div>
    <div class="login-panel">
      <div class="login-card">
        <div class="login-logo">AI</div>
        <h2>欢迎登录</h2>
        <p>AI 审核判读一致性校准与标准化管控平台</p>
        <el-form size="large" @submit.prevent="submit"
          ><el-form-item
            ><el-input
              v-model="form.username"
              :prefix-icon="User"
              placeholder="请输入用户名" /></el-form-item
          ><el-form-item
            ><el-input
              v-model="form.password"
              :prefix-icon="Lock"
              type="password"
              show-password
              placeholder="请输入密码"
              @keyup.enter="submit"
          /></el-form-item>
          <div class="login-options">
            <el-checkbox>记住账号</el-checkbox
            ><el-link type="primary">忘记密码？</el-link>
          </div>
          <el-button
            type="primary"
            native-type="submit"
            :loading="loading"
            class="login-button"
            >登录平台</el-button
          ></el-form
        >
        <div class="demo-hint">演示环境：任意账号密码均可登录</div>
      </div>
    </div>
  </div>
</template>
