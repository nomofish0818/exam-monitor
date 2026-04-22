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
import { ElNotification, ElMessageBox, ElMessage } from 'element-plus';
import CameraMonitor from '../../components/CameraMonitor.vue';

const router = useRouter();

// --- 狀態管理 ---
const isMonitorActive = ref(true);
const answers = reactive({});
let socket = null; // 統一管理 WebSocket 實例

// --- 1. 動態獲取身分資訊 ---
// 從 localStorage 讀取登入時儲存的 userId
const userId = localStorage.getItem('userId');
const role = 'student'; 
const examId = '101'; // 實際項目中可從 route.params.id 獲取

const mockQuestions = [
  { title: 'Vue3 中，哪一個 API 用於定義響應式對象？', options: ['ref', 'reactive', 'watch', 'computed'] },
  { title: 'SpringBoot 3 最低要求的 Java 版本是多少？', options: ['Java 8', 'Java 11', 'Java 17', 'Java 21'] },
  { title: 'WebSocket 通訊中，哪一個協議標識符代表加密連線？', options: ['ws://', 'wss://', 'http://', 'https://'] },
  { title: '在 Edge Computing 架構中，主要的運算發生在哪裡？', options: ['雲端伺服器', '用戶終端裝置', '資料庫中心', 'CDN 節點'] }
];

// --- 2. WebSocket 邏輯重構 ---
const initWebSocket = () => {
  // 安全檢查：如果沒登入，強制跳轉
  if (!userId) {
    ElMessage.error('偵測不到用戶資訊，請重新登入');
    router.push('/login');
    return;
  }

  // 構造動態 URL：必須與後端 @ServerEndpoint("/ws/monitor/{examId}/{role}/{userId}") 完全一致
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const socketUrl = `${protocol}//localhost:8080/ws/monitor/${examId}/${role}/${userId}`;
  
  console.log('🔗 正在建立連線:', socketUrl);
  socket = new WebSocket(socketUrl);

  socket.onopen = () => {
    console.log(`✅ 學生[${userId}] 考場指令系統連線成功`);
  };

  socket.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
      console.log('📩 收到監考指令:', data);

      // 處理警告指令 (WARN)
      if (data.action === 'WARN') {
        ElNotification({
          title: '監考老師提醒',
          message: data.msg || '請注意考試規範，正對攝像頭！',
          type: 'warning', // 警告建議用 warning 色調
          duration: 10000, // 警告建議停留久一點
          position: 'top-left'
        });
      }

      // 處理踢出指令 (KICK)
      if (data.action === 'KICK') {
        handleKickOut(data.msg || data.reason);
      }
    } catch (e) {
      console.error('解析 WebSocket 訊息失敗', e);
    }
  };

  socket.onclose = (e) => {
    console.warn('⚠️ 考場指令連線已斷開', e.code, e.reason);
  };

  socket.onerror = (err) => {
    console.error('❌ WebSocket 連線出錯', err);
  };
};

const handleKickOut = (reason) => {
  isMonitorActive.value = false;

  ElMessageBox.alert(
    `您的考試已被終止。原因：${reason || '違反監考規則'}`,
    '系統通知',
    {
      confirmButtonText: '確定並離開',
      type: 'error',
      showClose: false,
      callback: () => {
        // 清除考試狀態，跳轉回首頁
        router.push('/');
      }
    }
  );
};

// --- 生命週期 ---
onMounted(() => {
  initWebSocket();
});

onUnmounted(() => {
  if (socket) {
    socket.close();
    socket = null;
  }
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