<template>
  <div class="dashboard-container">
    <el-header class="dashboard-header">
      <h2>考試監控大屏 - 考場 ID: 101</h2>
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
import { ElMessage, ElMessageBox } from 'element-plus';
import axios from 'axios';

// --- 狀態管理 ---
const wsConnected = ref(false);
const dialogVisible = ref(false);
const selectedStudent = ref(null);

// 模擬學生列表 (實際開發中應從 API 獲取)
const studentList = ref([
  { id: 1002, realName: '張三', isAnomaly: false, anomalyCount: 0, lastAbnormalType: '', lastScreenshotUrl: '' },
  { id: 1003, realName: '李四', isAnomaly: false, anomalyCount: 0, lastAbnormalType: '', lastScreenshotUrl: '' },
  { id: 1004, realName: '王五', isAnomaly: false, anomalyCount: 0, lastAbnormalType: '', lastScreenshotUrl: '' },
  // ... 更多學生
]);

let socket = null;

// --- WebSocket 邏輯 ---
const initWebSocket = () => {
  const examId = '101';
  const role = 'teacher';
  const userId = '1001'; // 實際開發時，這個 ID 應該從登入的 Token 或 localStorage 中取得
  
  const wsUrl = `ws://localhost:8080/ws/monitor/${examId}/${role}/${userId}`;
  socket = new WebSocket(wsUrl);
  socket.onopen = () => {
    wsConnected.value = true;
    console.log('✅ 已成功連線至監控伺服器');
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
    // 後端返回的 /uploads/... 路徑，需拼接後端地址
    student.lastScreenshotUrl = `http://localhost:8080${data.screenshotUrl}`;
    
    // 播放提示音 (可選)
    const audio = new Audio('https://assets.mixkit.co/active_storage/sfx/2869/2869-preview.mp3');
    audio.play().catch(() => {});
  }
};

// --- 交互邏輯 ---
const handleCardClick = (student) => {
  selectedStudent.value = student;
  dialogVisible.value = true;
};

// 1. 強制終止
const terminateExam = async () => {
  try {
    await ElMessageBox.confirm(
      `確定要強制終止學生 ${selectedStudent.value.realName} 的考試嗎？`,
      '警告',
      { type: 'error' }
    );
    
    // 呼叫後端 API (需自行實作此 Controller 介面)
    const res = await axios.post('/api/monitor/terminate', {
      studentId: selectedStudent.value.id,
      examId: 101
    });

    if (res.data.code === 200) {
      ElMessage.success('已發送終止指令');
      dialogVisible.value = false;
    }
  } catch (e) {
    console.log('取消操作');
  }
};

// 2. 提醒按鈕
const sendReminder = () => {
  // 透過 WebSocket 發送訊息回伺服器，再由伺服器轉發給學生
  const payload = {
    action: 'REMIND',
    studentId: selectedStudent.value.id,
    message: '請正對攝像頭，勿亂動，注意考試規範！'
  };
  
  socket.send(JSON.stringify(payload));
  ElMessage.info(`已對 ${selectedStudent.value.realName} 發送提醒`);
  
  // 提醒後暫時解除紅框狀態 (看個人業務邏輯)
  selectedStudent.value.isAnomaly = false;
};

onMounted(() => {
  initWebSocket();
});

onUnmounted(() => {
  if (socket) socket.close();
});
</script>

<style scoped>
.dashboard-container {
  padding: 20px;
  background-color: #f5f7fa;
  min-height: 100vh;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  padding: 0 20px;
  border-bottom: 1px solid #ddd;
}

.student-card {
  margin-bottom: 20px;
  cursor: pointer;
  transition: transform 0.2s;
}

.student-card:hover {
  transform: translateY(-5px);
}

.student-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.name { font-weight: bold; font-size: 16px; }

.status-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}
.green { background-color: #67c23a; }
.red { background-color: #f56c6c; }

.student-id { color: #909399; font-size: 12px; }
.anomaly-type { color: #f56c6c; font-size: 12px; margin-top: 5px; font-weight: bold; }

/* 核心樣式：異常閃爍動畫 */
.anomaly-blink {
  border: 2px solid #f56c6c !important;
  animation: blink 1s infinite;
}

@keyframes blink {
  0% { background-color: #fff; }
  50% { background-color: #fef0f0; }
  100% { background-color: #fff; }
}

.evidence-img-container {
  width: 100%;
  height: 250px;
  background: #eee;
  display: flex;
  justify-content: center;
  align-items: center;
  margin: 15px 0;
  border-radius: 4px;
  overflow: hidden;
}

.dialog-footer-btns {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}
</style>