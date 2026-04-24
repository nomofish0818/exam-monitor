<template>
  <div class="camera-container" :class="{ 'minimized': isMinimized }">
    <div class="header">
      <h3>智能监考摄像头</h3>
      <button v-if="appState === 'exam'" @click="isMinimized = !isMinimized">
        {{ isMinimized ? '展开' : '缩小' }}
      </button>
    </div>
    
    <div class="video-wrapper" v-show="!isMinimized">
      <div v-if="appState === 'loading'" class="overlay loading-screen">
        <p>{{ loadingText }}</p>
      </div>

      <div v-else-if="appState === 'ready'" class="overlay ready-screen">
        <button class="start-btn" @click="startExam">确认并开始考试</button>
      </div>

      <video 
        v-show="appState === 'ready' || appState === 'exam'"
        ref="videoRef" autoplay muted playsinline 
        width="320" height="240"
      ></video>
    </div>

    <div v-if="appState === 'exam' && !isMinimized" class="status-box" :class="statusClass">
      {{ currentStatus }}
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue';
import request from '../utils/request';

// --- 配置与状态 ---
const lastUploadTime = ref(0); // 记录上次上传时间（防抖用）
const isUploading = ref(false); // 防止异步请求堆叠

/**
 * 核心处理函数：当收到 Worker 的异常信号时触发
 * @param {string} anomalyType 异常类型描述
 */
const handleAnomaly = async (anomalyType) => {
  const now = Date.now();
  
  // 1. 防抖逻辑：3秒内不重复执行
  if (now - lastUploadTime.value < 3000 || isUploading.value) {
    return;
  }

  lastUploadTime.value = now;
  isUploading.value = true;

  try {
  // 1. 执行截图与压缩
  const blob = await captureAndCompress();
  
  // 2. 从 localStorage 获取真实登录的学生 ID (我们在 Login.vue 存进去的)
  const realUserId = localStorage.getItem('userId');
  
  // 3. 获取考场 ID (目前测试阶段先写死 101，对齐 TeacherDashboard，未来可用 props 传入)
  const currentExamId = '101'; 

  // 🛡️ 安全校验：如果没有 userId，说明登录状态异常，终止上传
  if (!realUserId) {
    console.warn('⚠️ 无法获取 userId，放弃上传截图');
    return;
  }

  // 4. 封装 FormData
  const formData = new FormData();
  formData.append('file', blob, `anomaly_${Date.now()}.jpg`);
  formData.append('type', anomalyType);    // 必须与后端的 @RequestParam("type") 一致
  formData.append('userId', realUserId);   // 必须与后端的 @RequestParam("userId") 一致
  formData.append('examId', currentExamId);// 必须与后端的 @RequestParam("examId") 一致

  // 5. 发送请求 (确保你上方有 import request from '../utils/request')
  const response = await request.post('/api/monitor/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  });

  if (response.code === 200) {
    console.log('✅ 异常截图已成功上传至后端与数据库');
  } else {
    console.error('⚠️ 上传失败，后端回应:', response.message);
  }

} catch (error) {
  console.error('❌ 上传发生网络或服务器错误:', error);
}finally {
    isUploading.value = false;
  }
};

/**
 * 截取 <video> 画面并压缩为 Blob
 * @returns {Promise<Blob>}
 */
const captureAndCompress = () => {
  return new Promise((resolve) => {
    const video = videoRef.value;
    const canvas = document.createElement('canvas');
    
    // 保持与视频流原始比例一致
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    
    const ctx = canvas.getContext('2d');
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    
    // 压缩为 JPEG，品质 0.5 (约可将 640x480 的图压至 20-40KB)
    canvas.toBlob((blob) => {
      resolve(blob);
    }, 'image/jpeg', 0.5);
  });
};


// 状态管理
const videoRef = ref(null);
const appState = ref('loading'); 
const loadingText = ref('初始化中...');
const currentStatus = ref('系统准备中...');
const isMinimized = ref(false);

// Worker 与 Canvas
let aiWorker = null;
const inferenceCanvas = document.createElement('canvas');
inferenceCanvas.width = 320;
inferenceCanvas.height = 240;
const inferenceCtx = inferenceCanvas.getContext('2d', { willReadFrequently: true });

// 检测循环与心跳计数
let isDetecting = false;
let detectIntervalId = null;
let heartbeatIntervalId = null;

// 统计数据 (心跳包用)
let totalChecks = 0;
let anomalyChecks = 0;

const initializeSystem = async () => {
  appState.value = 'loading';
  loadingText.value = '开启摄像头中...';
  
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false });
    videoRef.value.srcObject = stream;
    await new Promise(resolve => { videoRef.value.onloadedmetadata = resolve; });
    videoRef.value.play();

    loadingText.value = '启动 AI 引擎...';
    
    // 初始化 Web Worker
    aiWorker = new Worker(new URL('../workers/ai.worker.js', import.meta.url), { type: 'module' });
    
    aiWorker.onmessage = handleWorkerMessage;
    
    // 通知 Worker 加载模型 (传入 public 目录下的 models 路径)
    aiWorker.postMessage({ type: 'INIT', payload: { modelPath: '/models' } });

  } catch (error) {
    alert('摄像头权限被拒绝或加载失败！');
  }
};

// 处理 Worker 传回的讯息
const handleWorkerMessage = (e) => {
  const { type, payload } = e.data;

  if (type === 'INIT_DONE') {
    appState.value = 'ready';
  } 
  else if (type === 'STATUS') {
    currentStatus.value = payload;
    isDetecting = false;
  }
  else if (type === 'CALIBRATION_DONE') {
    console.log(`无感校准完成，基准线: ${payload}`);
    currentStatus.value = '正常：单人端正作答中';
    startHeartbeat();
  }
  else if (type === 'RESULT') {
    const { status, isAnomaly } = payload;
    
    // 如果状态从正常变成异常，触发截图并上传给后端
    if (isAnomaly && !currentStatus.value.includes('异常')) {
      uploadEvidenceScreenshot(status); // 原本的逻辑
      
      // 👇 新增这行：触发上传到后端数据库的逻辑
      handleAnomaly(status); 
    }

    currentStatus.value = status;
    isDetecting = false;
    totalChecks++;
    if (isAnomaly) anomalyChecks++;
  }
  else if (type === 'ERROR') {
    console.error('❌ Worker 内部发生错误:', payload);
    alert('AI 初始化失败...\n错误讯息: ' + payload);
    loadingText.value = '加载失败';
    isDetecting = false;
  }
};

const startExam = () => {
  appState.value = 'exam';
  aiWorker.postMessage({ type: 'START_CALIBRATION' });
  
  // 启动主线程的抽帧循环 (每秒抽 1-2 帧传给 Worker)
  detectIntervalId = setInterval(sendFrameToWorker, 1000); 
};

// 抽取画面并交给 Worker
const sendFrameToWorker = () => {
  if (isDetecting || appState.value !== 'exam') return;
  
  const videoEl = videoRef.value;
  if (!videoEl || videoEl.readyState < 2) return;

  isDetecting = true;
  // 画到隐藏的 Canvas 上
  inferenceCtx.drawImage(videoEl, 0, 0, inferenceCanvas.width, inferenceCanvas.height);
  // 提取 ImageData (不传递 DOM 对象，Worker 可以直接处理 Uint8ClampedArray)
  const imageData = inferenceCtx.getImageData(0, 0, inferenceCanvas.width, inferenceCanvas.height);
  
  aiWorker.postMessage({ 
    type: 'DETECT', 
    payload: { imageData, width: inferenceCanvas.width, height: inferenceCanvas.height } 
  });
};

// 【通信层】发送心跳包
const startHeartbeat = () => {
  heartbeatIntervalId = setInterval(async () => {
    if (totalChecks === 0) return;
    
    const anomalyRatio = anomalyChecks / totalChecks;
    const payload = {
      timestamp: Date.now(),
      totalChecks,
      anomalyChecks,
      anomalyRatio: anomalyRatio.toFixed(2),
      currentStatus: currentStatus.value
    };

    try {
      // 替换为你的后端 API
      // await fetch('/api/exam/heartbeat', { method: 'POST', body: JSON.stringify(payload) });
      console.log('💓 发送心跳包:', payload);
      
      // 发送后重置统计
      totalChecks = 0;
      anomalyChecks = 0;
    } catch (err) {
      console.error('心跳包发送失败', err);
    }
  }, 10000); // 每 10 秒发送一次
};

// 【通信层】上传作弊证据
const uploadEvidenceScreenshot = async (reason) => {
  // 将 Canvas 压缩为 JPEG 格式 (质量 0.6) 以减少带宽消耗
  const base64Image = inferenceCanvas.toDataURL('image/jpeg', 0.6);
  
  const payload = {
    timestamp: Date.now(),
    reason: reason,
    image: base64Image
  };

  try {
    // 替换为你的后端 API
    // await fetch('/api/exam/evidence', { method: 'POST', body: JSON.stringify(payload) });
    console.log('📸 检测到异常，上传证据截图!', reason);
  } catch (err) {
    console.error('证据上传失败', err);
  }
};

const statusClass = computed(() => {
  if (currentStatus.value.includes('异常')) return 'error';
  if (currentStatus.value.includes('正常')) return 'success';
  return 'warning';
});

onMounted(() => { initializeSystem(); });

onUnmounted(() => {
  if (detectIntervalId) clearInterval(detectIntervalId);
  if (heartbeatIntervalId) clearInterval(heartbeatIntervalId);
  if (aiWorker) aiWorker.terminate();
  if (videoRef.value?.srcObject) {
    videoRef.value.srcObject.getTracks().forEach(track => track.stop());
  }
});
</script>

<style scoped>
/* 预设样式：考场右下角固定的小窗口 */
.camera-container {
  position: fixed;
  bottom: 20px;
  right: 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
  padding: 10px;
  z-index: 1000;
  transition: all 0.3s ease;
}

/* 缩小模式 */
.camera-container.minimized {
  width: auto;
  padding: 5px 15px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.header h3 { margin: 0; font-size: 14px; }
.header button { font-size: 12px; cursor: pointer; }

.video-wrapper {
  position: relative;
  width: 320px;
  height: 240px;
  background-color: #000;
  border-radius: 4px;
  overflow: hidden;
}

.overlay {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  color: white;
  z-index: 10;
}
.loading-screen { background-color: #111; }
.ready-screen { background-color: rgba(0, 0, 0, 0.5); }

.status-box {
  margin-top: 8px;
  padding: 8px;
  font-size: 14px;
  font-weight: bold;
  text-align: center;
  border-radius: 4px;
}
.success { color: #155724; background-color: #d4edda; }
.error { color: #721c24; background-color: #f8d7da; }
.warning { color: #856404; background-color: #fff3cd; }
</style>