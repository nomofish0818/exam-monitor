<template>
  <div class="dashboard-container">
    <el-header class="dashboard-header">
      <h2>考試監控大屏 - 考場 ID: {{ currentExamId }}</h2>
      <el-tag :type="wsConnected ? 'success' : 'danger'">
        {{ wsConnected ? '監控連線中' : '連線已斷開' }}
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
            <div class="student-id">學號: {{ student.id }}</div>
            <div class="anomaly-type" v-if="student.isAnomaly">
              {{ student.lastAbnormalType }}
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-main>

    <el-dialog
      v-model="dialogVisible"
      title="異常詳情與處理"
      width="500px"
      destroy-on-close
    >
      <div v-if="selectedStudent" class="dialog-content">
        <p><strong>學生：</strong> {{ selectedStudent.realName }} ({{ selectedStudent.id }})</p>
        <p><strong>異常類型：</strong> {{ selectedStudent.lastAbnormalType }}</p>
        
        <div class="evidence-img-container">
          <el-image 
            :src="selectedStudent.lastScreenshotUrl" 
            fit="contain"
            placeholder="圖片載入中..."
          >
            <template #error>
              <div class="image-slot">暫無證據圖片</div>
            </template>
          </el-image>
        </div>

        <div class="dialog-footer-btns">
          <el-button type="warning" @click="sendReminder">提醒注意</el-button>
          <el-button type="danger" @click="terminateExam">強制終止考試</el-button>
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

// --- 路由與狀態管理 ---
const route = useRoute();
const router = useRouter();

const wsConnected = ref(false);
const dialogVisible = ref(false);
const selectedStudent = ref(null);

// ⚡ 核心優化：不再寫死資料，從路由和本地緩存動態獲取
// 支援從 URL 獲取考場 ID (例如: /teacher/dashboard?examId=101) 默認 101
const currentExamId = ref(route.query.examId || '101'); 
const currentUserId = ref('');

// 模擬學生列表 (實際開發中應呼叫 API: request.get(`/api/exam/${currentExamId.value}/students`))
const studentList = ref([
  { id: 1002, realName: '張三', isAnomaly: false, anomalyCount: 0, lastAbnormalType: '', lastScreenshotUrl: '' },
  { id: 1003, realName: '李四', isAnomaly: false, anomalyCount: 0, lastAbnormalType: '', lastScreenshotUrl: '' },
  { id: 1004, realName: '王五', isAnomaly: false, anomalyCount: 0, lastAbnormalType: '', lastScreenshotUrl: '' },
]);

let socket = null;

// --- WebSocket 邏輯 ---
const initWebSocket = () => {
  // 從登入憑證獲取真實身份
  const userId = localStorage.getItem('userId');
  const token = localStorage.getItem('token');

  // 🛡️ 防線：如果無痕窗口沒登入，直接攔截並跳轉，避免後續接口 401 崩潰
  if (!userId || !token) {
    ElMessage.error('您尚未登錄或憑證已失效，請先登錄教師帳號！');
    router.push('/login');
    return;
  }
  
  currentUserId.value = userId;
  const role = 'teacher';
  
  // 動態拼接 WebSocket URL
  const wsUrl = `ws://localhost:8080/ws/monitor/${currentExamId.value}/${role}/${currentUserId.value}`;
  
  socket = new WebSocket(wsUrl);
  
  socket.onopen = () => {
    wsConnected.value = true;
    console.log(`✅ 教師 [${currentUserId.value}] 已成功連線至考場 [${currentExamId.value}]`);
  };

  socket.onmessage = (event) => {
    const data = JSON.parse(event.data);
    console.log('📩 收到異常通知:', data);

    if (data.type === 'NEW_ANOMALY') {
      updateStudentStatus(data);
    }
  };

  socket.onclose = () => {
    wsConnected.value = false;
    console.warn('❌ 監控連線已關閉');
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

// 1. 強制終止
const terminateExam = async () => {
  try {
    await ElMessageBox.confirm(
      `確定要強制終止學生 ${selectedStudent.value.realName} 的考試嗎？`,
      '嚴重警告',
      { type: 'error', confirmButtonText: '強制終止', cancelButtonText: '取消' }
    );
    
    const res = await request.post('/api/exam/terminate', {
      studentId: selectedStudent.value.id,
      reason: '嚴重違規，監考老師已執行強制退場。'
    });

    // ⚡ 修正 Bug: request 攔截器已解構 data，直接判斷 res.code 即可
    if (res.code === 200) {
      ElMessage.success(`已強制踢出 ${selectedStudent.value.realName}`);
      dialogVisible.value = false;
    } else {
      ElMessage.warning(res.message || '指令發送失敗');
    }
  } catch (e) {
    console.log('已取消操作');
  }
};

// 2. 提醒按鈕
const sendReminder = async () => {
  try {
    const res = await request.post('/api/exam/warn', {
      studentId: selectedStudent.value.id,
      message: '請正對攝像頭，勿亂動，注意考試規範！' 
    });

    if (res.code === 200) {
      ElMessage.info(`已對 ${selectedStudent.value.realName} 發送提醒`);
      selectedStudent.value.isAnomaly = false;
    } else {
      ElMessage.warning(res.message || '學生不在線上，發送失敗');
    }
  } catch (e) {
    console.error('發送提醒請求失敗:', e);
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
/* 樣式保持不變 */
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