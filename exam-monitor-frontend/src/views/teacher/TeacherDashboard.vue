<template>
  <div class="dashboard-container">
    <el-header class="dashboard-header">
      <h2>考试监控大屏 - 考场 ID: {{ currentExamId }}</h2>
      <el-tag :type="wsConnected ? 'success' : 'danger'">
        {{ wsConnected ? '监控连线中' : '连线已断开' }}
      </el-tag>
    </el-header>

    <el-main>
      <el-row :gutter="20">
        <el-col 
          v-for="student in studentList" 
          :key="student.id" 
          :xs="12" :sm="8" :md="6" :lg="4"
        >
          <el-card 
            :class="['student-card', { 'anomaly-blink': student.isAnomaly }]"
            @click="handleCardClick(student)"
          >
            <div class="student-info">
              <span class="name">{{ student.realName }}</span>
              <el-badge :value="student.anomalyCount" :hidden="student.anomalyCount === 0" class="item">
                <div :class="['status-dot', student.isAnomaly ? 'red' : 'green']"></div>
              </el-badge>
            </div>
            <div class="student-id">学号: {{ student.id }}</div>
            <div class="anomaly-type" v-if="student.isAnomaly">
              {{ student.lastAbnormalType }}
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-main>

    <el-dialog
      v-model="dialogVisible"
      title="异常详情与处理"
      width="500px"
      destroy-on-close
    >
      <div v-if="selectedStudent" class="dialog-content">
        <p><strong>学生：</strong> {{ selectedStudent.realName }} ({{ selectedStudent.id }})</p>
        <p><strong>异常类型：</strong> {{ selectedStudent.lastAbnormalType }}</p>
        
        <div class="evidence-img-container">
          <el-image 
            :src="selectedStudent.lastScreenshotUrl" 
            fit="contain"
            placeholder="图片加载中..."
          >
            <template #error>
              <div class="image-slot">暂无证据图片</div>
            </template>
          </el-image>
        </div>

        <div class="dialog-footer-btns">
          <el-button type="warning" @click="sendReminder">提醒注意</el-button>
          <el-button type="danger" @click="terminateExam">强制终止考试</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '../../utils/request';

// --- 路由与状态管理 ---
const route = useRoute();
const router = useRouter();

const wsConnected = ref(false);
const dialogVisible = ref(false);
const selectedStudent = ref(null);

// ⚡ 核心优化：不再写死资料，从路由和本地缓存动态获取
// 支持从 URL 获取考场 ID (例如: /teacher/dashboard?examId=101) 默认 101
const currentExamId = ref(route.query.examId || '101'); 
const currentUserId = ref('');

// 模拟学生列表 (实际开发中应呼叫 API: request.get(`/api/exam/${currentExamId.value}/students`))
const studentList = ref([
  { id: 1002, realName: '张三', isAnomaly: false, anomalyCount: 0, lastAbnormalType: '', lastScreenshotUrl: '' },
  { id: 1003, realName: '李四', isAnomaly: false, anomalyCount: 0, lastAbnormalType: '', lastScreenshotUrl: '' },
  { id: 1004, realName: '王五', isAnomaly: false, anomalyCount: 0, lastAbnormalType: '', lastScreenshotUrl: '' },
]);

let socket = null;

// --- WebSocket 逻辑 ---
const initWebSocket = () => {
  // 从登录凭证获取真实身份
  const userId = localStorage.getItem('userId');
  const token = localStorage.getItem('token');

  // 🛡️ 防线：如果无痕窗口没登录，直接拦截并跳转，避免后续接口 401 崩溃
  if (!userId || !token) {
    ElMessage.error('您尚未登录或凭证已失效，请先登录教师帐号！');
    router.push('/login');
    return;
  }
  
  currentUserId.value = userId;
  const role = 'teacher';
  
  // 动态拼接 WebSocket URL
  const wsUrl = `ws://localhost:8080/ws/monitor/${currentExamId.value}/${role}/${currentUserId.value}`;
  
  socket = new WebSocket(wsUrl);
  
  socket.onopen = () => {
    wsConnected.value = true;
    console.log(`✅ 教师 [${currentUserId.value}] 已成功连线至考场 [${currentExamId.value}]`);
  };

  socket.onmessage = (event) => {
    const data = JSON.parse(event.data);
    console.log('📩 收到异常通知:', data);

    if (data.type === 'NEW_ANOMALY') {
      updateStudentStatus(data);
    }
  };

  socket.onclose = () => {
    wsConnected.value = false;
    console.warn('❌ 监控连线已关闭');
  };
};

const updateStudentStatus = (data) => {
  const student = studentList.value.find(s => s.id === data.studentId);
  if (student) {
    student.isAnomaly = true;
    student.anomalyCount++;
    student.lastAbnormalType = data.abnormalType;
    student.lastScreenshotUrl = `http://localhost:8080${data.screenshotUrl}`;
    
    // 播放提示音
    const audio = new Audio('https://assets.mixkit.co/active_storage/sfx/2869/2869-preview.mp3');
    audio.play().catch(() => {});
  }
};

const handleCardClick = (student) => {
  selectedStudent.value = student;
  dialogVisible.value = true;
};

// 1. 强制终止
const terminateExam = async () => {
  try {
    await ElMessageBox.confirm(
      `确定要强制终止学生 ${selectedStudent.value.realName} 的考试吗？`,
      '严重警告',
      { type: 'error', confirmButtonText: '强制终止', cancelButtonText: '取消' }
    );
    
    const res = await request.post('/api/exam/terminate', {
      studentId: selectedStudent.value.id,
      reason: '严重违规，监考老师已执行强制退场。'
    });

    // ⚡ 修正 Bug: request 拦截器已解构 data，直接判断 res.code 即可
    if (res.code === 200) {
      ElMessage.success(`已强制踢出 ${selectedStudent.value.realName}`);
      dialogVisible.value = false;
    } else {
      ElMessage.warning(res.message || '指令发送失败');
    }
  } catch (e) {
    console.log('已取消操作');
  }
};

// 2. 提醒按钮
const sendReminder = async () => {
  try {
    const res = await request.post('/api/exam/warn', {
      studentId: selectedStudent.value.id,
      message: '请正对摄像头，勿乱动，注意考试规范！' 
    });

    if (res.code === 200) {
      ElMessage.info(`已对 ${selectedStudent.value.realName} 发送提醒`);
      selectedStudent.value.isAnomaly = false;
    } else {
      ElMessage.warning(res.message || '学生不在线上，发送失败');
    }
  } catch (e) {
    console.error('发送提醒请求失败:', e);
  }
};

onMounted(() => {
  initWebSocket();
});

onUnmounted(() => {
  if (socket) socket.close();
});
</script>

<style scoped>
/* 样式保持不变 */
.dashboard-container { padding: 20px; background-color: #f5f7fa; min-height: 100vh; }
.dashboard-header { display: flex; justify-content: space-between; align-items: center; background: #fff; padding: 0 20px; border-bottom: 1px solid #ddd; }
.student-card { margin-bottom: 20px; cursor: pointer; transition: transform 0.2s; }
.student-card:hover { transform: translateY(-5px); }
.student-info { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.name { font-weight: bold; font-size: 16px; }
.status-dot { width: 12px; height: 12px; border-radius: 50%; }
.green { background-color: #67c23a; }
.red { background-color: #f56c6c; }
.student-id { color: #909399; font-size: 12px; }
.anomaly-type { color: #f56c6c; font-size: 12px; margin-top: 5px; font-weight: bold; }
.anomaly-blink { border: 2px solid #f56c6c !important; animation: blink 1s infinite; }
@keyframes blink { 0% { background-color: #fff; } 50% { background-color: #fef0f0; } 100% { background-color: #fff; } }
.evidence-img-container { width: 100%; height: 250px; background: #eee; display: flex; justify-content: center; align-items: center; margin: 15px 0; border-radius: 4px; overflow: hidden; }
.dialog-footer-btns { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }
</style>