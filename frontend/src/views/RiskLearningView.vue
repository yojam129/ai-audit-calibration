<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { learningApi, riskApi } from "../api/modules";
import { useAuthStore } from "../stores/auth";
import type {
  Exam,
  LearningTask,
  ReviewerErrorFocus,
  RiskPolicy,
  RiskProfile,
} from "../types";

const auth = useAuthStore();
const canManageRisk = computed(() => auth.hasPermission("risk:manage"));
const tab = ref(canManageRisk.value ? "risk" : "learning");
const risks = ref<RiskProfile[]>([]);
const errors = ref<ReviewerErrorFocus[]>([]);
const tasks = ref<LearningTask[]>([]);
const policy = ref<RiskPolicy>({ qualificationAccuracyThreshold: 0.1 });
const thresholdPercent = ref(10);
const loading = ref(false);
const examVisible = ref(false);
const activeId = ref(0);
const exam = ref<Exam>();
const answers = reactive<Record<number, string[]>>({});

const taskId = (row: LearningTask) => row.assignmentId || row.id;
const formatTime = (value?: string) => value ? value.replace("T", " ").replace(/Z$/, "").slice(0, 19) : "-";
const formatLabel = (value?: string) => ({
  POSITIVE: "阳性",
  NEGATIVE: "阴性",
  INDETERMINATE: "可疑",
  INVALID: "无效",
}[value || ""] || value || "-");
const formatLevel = (value?: string) => ({
  HIGH: "高风险",
  WATCH: "关注",
  NORMAL: "正常",
  INSUFFICIENT_DATA: "数据不足",
}[value || ""] || value || "-");
const formatTaskStatus = (value?: string) => ({
  ASSIGNED: "待完成培训",
  LEARNING_REQUIRED: "待完成培训",
  EXAM_REQUIRED: "待参加考试",
  RESTORE_PENDING: "资格恢复中",
  RESTORED: "审核资格已恢复",
}[value || ""] || value || "-");

async function load() {
  loading.value = true;
  try {
    const learning = await learningApi.page({ current: 1, size: 100 });
    tasks.value = learning.records;
    if (canManageRisk.value) {
      const [riskPage, errorPage, currentPolicy] = await Promise.all([
        riskApi.page({ current: 1, size: 100 }),
        riskApi.errors({ current: 1, size: 100 }),
        riskApi.policy(),
      ]);
      risks.value = riskPage.records;
      errors.value = errorPage.records;
      policy.value = currentPolicy;
      thresholdPercent.value = currentPolicy.qualificationAccuracyThreshold * 100;
    } else {
      const errorPage = await riskApi.myErrors({ current: 1, size: 100 });
      errors.value = errorPage.records;
    }
  } catch {
    ElMessage.error("风控或培训服务加载失败");
  } finally {
    loading.value = false;
  }
}

async function savePolicy() {
  policy.value = await riskApi.updatePolicy(thresholdPercent.value / 100);
  thresholdPercent.value = policy.value.qualificationAccuracyThreshold * 100;
  ElMessage.success("审核资格阈值已更新");
}

async function completeTraining(row: LearningTask) {
  await learningApi.completeTraining(taskId(row));
  ElMessage.success("Flowable 培训任务已完成，请参加专项考试");
  await load();
}

async function start(row: LearningTask) {
  activeId.value = taskId(row);
  exam.value = await learningApi.startExam(activeId.value);
  Object.keys(answers).forEach((key) => delete answers[+key]);
  examVisible.value = true;
}

async function submit() {
  if (!exam.value) return;
  await learningApi.submitExam(activeId.value, {
    attemptId: exam.value.attemptId,
    answers: exam.value.questions.map((question) => ({
      questionId: question.id,
      selectedOptions: answers[question.id] || [],
    })),
  });
  ElMessage.success("考试已提交；全部答对后将自动恢复审核资格");
  examVisible.value = false;
  await load();
}

onMounted(load);
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="page-intro">
      <div>
        <h1>风控与培训</h1>
        <p>终审真值驱动错误通道留痕、强制培训、满分考试与审核资格恢复。</p>
      </div>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane v-if="canManageRisk" label="人员风险" name="risk">
        <section class="panel policy-bar">
          <div>
            <strong>审核资格冻结阈值</strong>
            <span>最近50个审核样本满50条后，准确率低于该值才自动禁用审核权限。</span>
          </div>
          <div class="policy-control">
            <el-input-number v-model="thresholdPercent" :min="0" :max="100" :precision="1" />
            <span>%</span>
            <el-button type="primary" @click="savePolicy">保存</el-button>
          </div>
        </section>
        <section class="panel">
          <el-table :data="risks">
            <el-table-column prop="reviewerId" label="审核员" min-width="150" />
            <el-table-column prop="reviewed" label="累计审核样本" width="125" />
            <el-table-column label="累计正确" width="100"><template #default="{ row }">{{ row.correct }}</template></el-table-column>
            <el-table-column label="累计准确率" width="120"><template #default="{ row }">{{ (row.accuracy * 100).toFixed(2) }}%</template></el-table-column>
            <el-table-column label="近期样本" width="110"><template #default="{ row }">{{ row.recentReviewed }} / 50</template></el-table-column>
            <el-table-column label="近期准确率" width="125"><template #default="{ row }">{{ row.recentReviewed ? `${(row.recentAccuracy * 100).toFixed(2)}%` : "-" }}</template></el-table-column>
            <el-table-column label="冻结判定" width="120"><template #default="{ row }"><el-tag :type="row.recentWindowReady ? 'warning' : 'info'">{{ row.recentWindowReady ? "窗口已满" : "暂不判定" }}</el-tag></template></el-table-column>
            <el-table-column label="风险等级" width="120"><template #default="{ row }">{{ formatLevel(row.level) }}</template></el-table-column>
            <el-table-column label="审核资格" width="140"><template #default="{ row }"><el-tag :type="row.trainingRequired ? 'danger' : 'success'">{{ row.trainingRequired ? "已冻结" : "正常" }}</el-tag></template></el-table-column>
          </el-table>
        </section>
      </el-tab-pane>

      <el-tab-pane label="培训任务" name="learning">
        <section class="panel">
          <el-table :data="tasks">
            <el-table-column prop="reviewerId" label="学员" min-width="140" />
            <el-table-column prop="errorType" label="重点错误类型" min-width="170" />
            <el-table-column prop="focusSampleNo" label="重点样本" min-width="160"><template #default="{ row }">{{ row.focusSampleNo || row.focusSampleId || "-" }}</template></el-table-column>
            <el-table-column label="错误通道" min-width="160"><template #default="{ row }">{{ [row.focusChamber && `${row.focusChamber}腔`, row.focusChannelCode].filter(Boolean).join(" / ") || "-" }}</template></el-table-column>
            <el-table-column prop="focusTargetCode" label="靶标" min-width="120" />
            <el-table-column label="状态" width="140"><template #default="{ row }">{{ formatTaskStatus(row.status) }}</template></el-table-column>
            <el-table-column label="流程实例" min-width="190"><template #default="{ row }">{{ row.processInstanceId || "流程启动中" }}</template></el-table-column>
            <el-table-column prop="bestScore" label="最高分" width="100" />
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <el-button v-if="['ASSIGNED', 'LEARNING_REQUIRED'].includes(row.status)" :disabled="!row.processInstanceId" link type="primary" @click="completeTraining(row)">完成培训</el-button>
                <el-button v-if="row.status === 'EXAM_REQUIRED'" :disabled="!row.processInstanceId" link type="danger" @click="start(row)">参加考试</el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </el-tab-pane>

      <el-tab-pane label="错误通道清单" name="errors">
        <section class="panel">
          <el-table :data="errors">
            <el-table-column v-if="canManageRisk" prop="reviewerId" label="审核员" min-width="140" />
            <el-table-column label="样本编号" min-width="170"><template #default="{ row }">{{ row.sampleNo || row.sampleId || "-" }}</template></el-table-column>
            <el-table-column prop="chamber" label="腔室" width="80" />
            <el-table-column prop="channelCode" label="通道" width="100" />
            <el-table-column prop="targetCode" label="靶标" min-width="120" />
            <el-table-column label="一级判读" width="100"><template #default="{ row }">{{ formatLabel(row.predictedLabel) }}</template></el-table-column>
            <el-table-column label="终审真值" width="100"><template #default="{ row }">{{ formatLabel(row.truthLabel) }}</template></el-table-column>
            <el-table-column prop="errorType" label="错误类型" min-width="180" />
            <el-table-column label="终审时间" min-width="170"><template #default="{ row }">{{ formatTime(row.occurredAt) }}</template></el-table-column>
          </el-table>
        </section>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="examVisible" title="专项校准考试（必须全部答对）" width="680px">
      <div v-for="(question, index) in exam?.questions" :key="question.id" class="question">
        <strong>{{ index + 1 }}. {{ question.stem }}（{{ question.score }}分）</strong>
        <el-checkbox-group v-model="answers[question.id]">
          <el-checkbox v-for="option in question.options" :key="option" :value="option">{{ option }}</el-checkbox>
        </el-checkbox-group>
      </div>
      <template #footer>
        <el-button @click="examVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">提交答案</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.policy-bar { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 16px; }
.policy-bar strong { display: block; margin-bottom: 6px; }
.policy-bar span { color: var(--text-secondary); }
.policy-control { display: flex; align-items: center; gap: 10px; flex: none; }
.question { margin-bottom: 24px; }
.question strong { display: block; margin-bottom: 12px; }
@media (max-width: 720px) { .policy-bar { align-items: flex-start; flex-direction: column; } }
</style>
