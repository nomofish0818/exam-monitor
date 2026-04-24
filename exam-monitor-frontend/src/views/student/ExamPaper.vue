<template>
  <div class="exam-layout">
    <div class="exam-content">
      <el-card class="paper-card">
        <template #header>
          <div class="paper-header">
            <h2>2024 年度专业技术模拟考试</h2>
            <el-tag type="info">剩余时间：59:22</el-tag>
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
          <el-button type="primary" size="large">提交试卷</el-button>
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

// --- 状态管理 ---
const isMonitorActive = ref(true);
const answers = reactive({});
let socket = null; // 统一管理 WebSocket 实例

// --- 1. 动态获取身分信息 ---
// 从 localStorage 读取登录时储存的 userId
const userId = localStorage.getItem('userId');
const role = 'student'; 
const examId = '101'; // 实际项目中可从 route.params.id 获取

const mockQuestions = [
  { title: 'Vue3 中，哪一个 API 用于定义响应式对象？', options: ['ref', 'reactive', 'watch', 'computed'] },
  { title: 'SpringBoot 3 最低要求的 Java 版本是多少？', options: ['Java 8', 'Java 11', 'Java 17', 'Java 21'] },
  { title: 'WebSocket 通讯中，哪一个协议标识符代表加密连接？', options: ['ws://', 'wss://', 'http://', 'https://'] },
  { title: '在 Edge Computing 架构中，主要的运算发生在哪里？', options: ['云端服务器', '用户终端装置', '数据库中心', 'CDN 节点'] }
];

// --- 2. WebSocket 逻辑重构 ---
const initWebSocket = () => {
  // 安全检查：如果没登录，强制跳转
  if (!userId) {
    ElMessage.error('侦测不到用户信息，请重新登录');
    router.push('/login');
    return;
  }

  // 构造动态 URL：必须与后端 @ServerEndpoint("/ws/monitor/{examId}/{role}/{userId}") 完全一致
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const socketUrl = `${protocol}//localhost:8080/ws/monitor/${examId}/${role}/${userId}`;
  
  console.log('🔗 正在建立连线:', socketUrl);
  socket = new WebSocket(socketUrl);

  socket.onopen = () => {
    console.log(`✅ 学生[${userId}] 考场指令系统连线成功`);
  };

  socket.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
      console.log('📩 收到监考指令:', data);

      // 处理警告指令 (WARN)
      if (data.action === 'WARN') {
        ElNotification({
          title: '监考老师提醒',
          message: data.msg || '请注意考试规范，正对摄像头！',
          type: 'warning', // 警告建议用 warning 色调
          duration: 10000, // 警告建议停留久一点
          position: 'top-left'
        });
      }

      // 处理踢出指令 (KICK)
      if (data.action === 'KICK') {
        handleKickOut(data.msg || data.reason);
      }
    } catch (e) {
      console.error('解析 WebSocket 讯息失败', e);
    }
  };

  socket.onclose = (e) => {
    console.warn('⚠️ 考场指令连线已断开', e.code, e.reason);
  };

  socket.onerror = (err) => {
    console.error('❌ WebSocket 连线出错', err);
  };
};

const handleKickOut = (reason) => {
  isMonitorActive.value = false;

  ElMessageBox.alert(
    `您的考试已被终止。原因：${reason || '违反监考规则'}`,
    '系统通知',
    {
      confirmButtonText: '确定并离开',
      type: 'error',
      showClose: false,
      callback: () => {
        // 清除考试状态，跳转回首页
        router.push('/');
      }
    }
  );
};

// --- 生命周期 ---
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

/* 监控组件的容器样式 */
.monitor-section {
  width: 350px;
  /* 这里可以根据 CameraMonitor 的内部 fixed 定位做调整 */
}
</style>