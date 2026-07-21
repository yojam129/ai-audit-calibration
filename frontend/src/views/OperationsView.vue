<script setup lang="ts">
import axios from "axios";
import { onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { integrationApi, modelApi, notificationApi } from "../api/modules";
import type {
  ImportBatch,
  ImportError,
  ModelVersion,
  Notification,
  NotificationPreference,
} from "../types";
import { formatBusinessType, formatDateTime, formatStatus } from "../utils/format";
const route = useRoute();
const tab = ref(route.query.tab === "notifications" ? "notifications" : "imports"),
  batches = ref<ImportBatch[]>([]),
  errors = ref<ImportError[]>([]),
  notices = ref<Notification[]>([]);
const userId = localStorage.getItem("auth_user_id") || "current";
const unread = ref(0);
const importType = ref("FLUORESCENCE");
const preference = reactive<NotificationPreference>({
  userId,
  inAppEnabled: true,
  emailEnabled: false,
  eventTypes: [],
});
const modelCode = ref("curve-classifier"),
  currentModel = ref<ModelVersion>();
const modelForm = reactive({
  modelCode: "curve-classifier",
  version: "",
  runtime: "ONNX_RUNTIME",
  artifactUri: "",
  checksum: "",
  metricsJson: "{}",
});
async function loadImports() {
  batches.value = (
    await integrationApi.page({ pageNo: 1, pageSize: 100 })
  ).records;
}
async function loadNotifications() {
  notices.value = (
    await notificationApi.page({ current: 1, size: 100, userId })
  ).records;
  unread.value = await notificationApi.unread(userId);
  Object.assign(preference, await notificationApi.preference(userId));
}
async function upload(file: File) {
  const hash = Array.from(
    new Uint8Array(
      await crypto.subtle.digest("SHA-256", await file.arrayBuffer()),
    ),
  )
    .map((x) => x.toString(16).padStart(2, "0"))
    .join("");
  const p = await integrationApi.presign({
    fileName: file.name,
    contentType: file.type || "application/octet-stream",
    sizeBytes: file.size,
    sha256: hash,
  });
  await axios.put(p.uploadUrl, file, {
    headers: { "Content-Type": file.type || "application/octet-stream" },
  });
  await integrationApi.confirm({
    assetId: p.assetId,
    sizeBytes: file.size,
    sha256: hash,
  });
  await integrationApi.createImport({
    assetId: p.assetId,
    businessType: importType.value,
    templateVersion: "v1",
  });
  ElMessage.success("文件已上传并创建导入批次");
  await loadImports();
}
async function showErrors(id: number) {
  errors.value = await integrationApi.errors(id);
}
async function savePreference() {
  await notificationApi.updatePreference(userId, {
    email: preference.email,
    inAppEnabled: preference.inAppEnabled,
    emailEnabled: preference.emailEnabled,
    eventTypes: preference.eventTypes,
  });
  ElMessage.success("通知偏好已保存");
}
async function markRead(n: Notification) {
  await notificationApi.read(n.id);
  await loadNotifications();
}
async function queryModel() {
  currentModel.value = await modelApi.current(modelCode.value);
}
async function registerModel() {
  currentModel.value = await modelApi.register(modelForm);
  ElMessage.success("模型版本已登记");
}
onMounted(() =>
  Promise.all([loadImports(), loadNotifications()]).catch(() =>
    ElMessage.error("运营数据加载失败"),
  ),
);
watch(
  () => route.query.tab,
  (value) => {
    if (value === "notifications" || value === "imports" || value === "models") {
      tab.value = value;
    }
  },
);
</script>
<template>
  <div class="page">
    <div class="page-intro">
      <div>
        <h1>运营管理</h1>
        <p>文件导入、通知偏好和模型版本均连接真实后端服务</p>
      </div>
    </div>
    <el-tabs v-model="tab">
      <el-tab-pane label="文件与导入" name="imports"
        ><section class="panel">
          <el-radio-group v-model="importType" style="margin-right: 12px">
            <el-radio-button value="FLUORESCENCE">荧光曲线数据</el-radio-button>
            <el-radio-button value="POSITIVE_RATE_HISTORY">历史阳性率数据</el-radio-button>
          </el-radio-group>
          <el-upload
            :show-file-list="false"
            :http-request="({ file }: any) => upload(file)"
            ><el-button type="primary">上传表格并导入</el-button></el-upload
          ><el-table :data="batches"
            ><el-table-column prop="batchNo" label="批次" /><el-table-column
              label="业务类型"
            ><template #default="{ row }">{{ formatBusinessType(row.businessType) }}</template></el-table-column><el-table-column label="状态"><template #default="{ row }">{{ formatStatus(row.status) }}</template></el-table-column><el-table-column
              prop="totalRows"
              label="总行"
            /><el-table-column
              prop="successRows"
              label="成功"
            /><el-table-column prop="errorRows" label="失败" /><el-table-column
              label="操作"
              ><template #default="{ row }"
                ><el-button link @click="showErrors(row.id)"
                  >错误明细</el-button
                ></template
              ></el-table-column
            ></el-table
          ><el-table :data="errors"
            ><el-table-column prop="rowNo" label="行" /><el-table-column
              prop="columnName"
              label="列" /><el-table-column
              prop="errorCode"
              label="错误码" /><el-table-column
              prop="errorMessage"
              label="原因"
          /></el-table></section
      ></el-tab-pane>
      <el-tab-pane :label="`通知中心 (${unread})`" name="notifications"
        ><section class="panel">
          <el-form inline
            ><el-form-item label="邮箱"
              ><el-input v-model="preference.email" /></el-form-item
            ><el-form-item
              ><el-switch
                v-model="preference.inAppEnabled"
                active-text="站内" /></el-form-item
            ><el-form-item
              ><el-switch
                v-model="preference.emailEnabled"
                active-text="邮件" /></el-form-item
            ><el-button type="primary" @click="savePreference"
              >保存偏好</el-button
            ></el-form
          ><el-table :data="notices"
            ><el-table-column prop="subject" label="主题" /><el-table-column
              label="发送状态"><template #default="{ row }">{{ formatStatus(row.status) }}</template></el-table-column><el-table-column label="时间"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column><el-table-column
              label="操作"
              ><template #default="{ row }"
                ><el-button v-if="!row.read" link @click="markRead(row)"
                  >标记已读</el-button
                ></template
              ></el-table-column
            ></el-table
          >
        </section></el-tab-pane
      >
      <el-tab-pane label="模型版本" name="models"
        ><section class="panel">
          <el-input v-model="modelCode" style="width: 260px" /><el-button
            @click="queryModel"
            >查询当前版本</el-button
          ><el-descriptions v-if="currentModel" border
            ><el-descriptions-item label="版本">{{
              currentModel.version
            }}</el-descriptions-item
            ><el-descriptions-item label="状态">{{
              formatStatus(currentModel.status)
            }}</el-descriptions-item
            ><el-descriptions-item label="流量"
              >{{ currentModel.trafficPercent }}%</el-descriptions-item
            ></el-descriptions
          ><el-form :model="modelForm" label-width="100px"
            ><el-form-item label="模型代码"
              ><el-input v-model="modelForm.modelCode" /></el-form-item
            ><el-form-item label="版本"
              ><el-input v-model="modelForm.version" /></el-form-item
            ><el-form-item label="制品地址"
              ><el-input v-model="modelForm.artifactUri" /></el-form-item
            ><el-form-item label="校验和"
              ><el-input v-model="modelForm.checksum" /></el-form-item
            ><el-button type="primary" @click="registerModel"
              >登记版本</el-button
            ><el-button
              v-if="currentModel"
              @click="modelApi.deploy(currentModel.id, 100)"
              >激活</el-button
            ><el-button
              v-if="currentModel"
              @click="modelApi.deploy(currentModel.id, 10)"
              >灰度10%</el-button
            ><el-button
              v-if="currentModel"
              type="danger"
              @click="modelApi.rollback(currentModel.id, 'manual rollback')"
              >回滚</el-button
            ></el-form
          >
        </section></el-tab-pane
      >
    </el-tabs>
  </div>
</template>
