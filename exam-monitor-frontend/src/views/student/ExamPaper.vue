<template>
  <div class="exam-layout">
    <div class="exam-content">
      <el-card class="paper-card">
        <template #header>
          <div class="paper-header">
            <h2>2024 年度專業技術模擬考試</h2>
            <el-tag type="info">剩餘時間：59:22</el-tag>
          </div>
        </template>

        <div class="question-list">
          <div v-for="(q, index) in mockQuestions" :key="index" class="question-item">
            <p class="question-title">{{ index + 1 }}. {{ q.title }}</p>
            <el-radio-group v-model="answers[index]">
              <el-radio v-for="opt in q.options" :key="opt" :value="opt">
                {{ opt }}
              </el-radio>
            </el-radio-group>
          </div>
        </div>

        <div class="submit-bar">
          <el-button type="primary" size="large">提交試卷</el-button>
        </div>
      </el-card>
    </div>

    <div class="monitor-section" v-if="isMonitorActive">
      <CameraMonitor />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElNotification, ElMessageBox } from 'element-plus';
import CameraMonitor from '../../components/CameraMonitor.vue';

const router = useRouter();

// --- 考試數據 ---
const isMonitorActive = ref(true);
const answers = reactive({});
const mockQuestions = [
  { title: 'Vue3 中，哪一個 API 用於定義響應式對象？', options: ['ref', 'reactive', 'watch', 'computed'] },
  { title: 'SpringBoot 3 最低要求的 Java 版本是多少？', options: ['Java 8', 'Java 11', 'Java 17', 'Java 21'] },
  { title: 'WebSocket 通訊中，哪一個協議標識符代表加密連線？', options: ['ws://', 'wss://', 'http://', 'https://'] },
  { title: '在 Edge Computing 架構中，主要的運算發生在哪裡？', options: ['雲端伺服器', '用戶終端裝置', '資料庫中心', 'CDN 節點'] }
];

// --- WebSocket 邏輯 ---
let socket = null;

const initWebSocket = () => {
  /**
   * 連線網址根據先前擴展的後端邏輯：/ws/monitor/{role}/{userId}
   * 這裡模擬學生 ID 為 202
   */
  socket = new WebSocket('ws://localhost:8080/ws/monitor/student/202');

  socket.onopen = () => {
    console.log('✅ 考場指令系統連線成功');
  };

  socket.onmessage = (event) => {
    const data = JSON.parse(event.data);
    console.log('📩 收到監考指令:', data);

    // 1. 處理警告指令
    if (data.action === 'WARN') {
      ElNotification({
        title: '監考老師提醒',
        message: data.msg || '請注意考試規範，正對攝像頭！',
        type: 'error',
        duration: 5000, // 停留 5 秒
        position: 'top-left'
      });
    }

    // 2. 處理踢出指令
    if (data.action === 'KICK') {
      handleKickOut(data.reason);
    }
  };

  socket.onclose = () => {
    console.warn('⚠️ 考場指令連線已斷開');
  };
};

const handleKickOut = (reason) => {
  // 1. 先銷毀監控組件，確保攝像頭關閉
  isMonitorActive.value = false;

  // 2. 彈出無法關閉的警告視窗
  ElMessageBox.alert(
    `您的考試已被終止。原因：${reason || '違反監考規則'}`,
    '系統通知',
    {
      confirmButtonText: '確定並離開',
      type: 'error',
      showClose: false,
      callback: () => {
        // 3. 強制跳轉至登入頁或首頁
        router.push('/');
      }
    }
  );
};

onMounted(() => {
  initWebSocket();
});

onUnmounted(() => {
  if (socket) socket.close();
});
</script>

<style scoped>
.exam-layout {
  display: flex;
  min-height: 100vh;
  background-color: #f0f2f5;
  padding: 20px;
  gap: 20px;
}

.exam-content {
  flex: 1;
  max-width: 900px;
  margin: 0 auto;
}

.paper-card {
  min-height: 80vh;
}

.paper-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.question-item {
  margin-bottom: 30px;
  padding: 15px;
  border-bottom: 1px solid #eee;
}

.question-title {
  font-size: 16px;
  font-weight: bold;
  margin-bottom: 15px;
}

.submit-bar {
  text-align: center;
  margin-top: 40px;
}

/* 監控組件的容器樣式 */
.monitor-section {
  width: 350px;
  /* 這裡可以根據 CameraMonitor 的內部 fixed 定位做調整 */
}
</style>