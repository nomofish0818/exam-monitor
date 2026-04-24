<template>
  <div class="camera-container" :class="{ 'minimized': isMinimized }">
    <div class="header">
      <h3>智能監考攝像頭</h3>
      <button v-if="appState === 'exam'" @click="isMinimized = !isMinimized">
        {{ isMinimized ? '展開' : '縮小' }}
      </button>
    </div>
    
    <div class="video-wrapper" v-show="!isMinimized">
      <div v-if="appState === 'loading'" class="overlay loading-screen">
        <p>{{ loadingText }}</p>
      </div>

      <div v-else-if="appState === 'ready'" class="overlay ready-screen">
        <button class="start-btn" @click="startExam">確認並開始考試</button>
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

// --- 配置與狀態 ---
const lastUploadTime = ref(0); // 記錄上次上傳時間（防抖用）
const isUploading = ref(false); // 防止異步請求堆疊

/**
 * 核心處理函數：當收到 Worker 的異常信號時觸發
 * @param {string} anomalyType 異常類型描述
 */
const handleAnomaly = async (anomalyType) => {
  const now = Date.now();
  
  // 1. 防抖邏輯：3秒內不重複執行
  if (now - lastUploadTime.value < 3000 || isUploading.value) {
    return;
  }

  lastUploadTime.value = now;
  isUploading.value = true;

  try {
  // 1. 執行截圖與壓縮
  const blob = await captureAndCompress();
  
  // 2. 從 localStorage 獲取真實登入的學生 ID (我們在 Login.vue 存進去的)
  const realUserId = localStorage.getItem('userId');
  
  // 3. 獲取考場 ID (目前測試階段先寫死 101，對齊 TeacherDashboard，未來可用 props 傳入)
  const currentExamId = '101'; 

  // 🛡️ 安全校驗：如果沒有 userId，說明登入狀態異常，終止上傳
  if (!realUserId) {
    console.warn('⚠️ 無法獲取 userId，放棄上傳截圖');
    return;
  }

  // 4. 封裝 FormData
  const formData = new FormData();
  formData.append('file', blob, `anomaly_${Date.now()}.jpg`);
  formData.append('type', anomalyType);    // 必須與後端的 @RequestParam("type") 一致
  formData.append('userId', realUserId);   // 必須與後端的 @RequestParam("userId") 一致
  formData.append('examId', currentExamId);// 必須與後端的 @RequestParam("examId") 一致

  // 5. 發送請求 (確保你上方有 import request from '../utils/request')
  const response = await request.post('/api/monitor/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  });

  if (response.code === 200) {
    console.log('✅ 異常截圖已成功上傳至後端與資料庫');
  } else {
    console.error('⚠️ 上傳失敗，後端回應:', response.message);
  }

} catch (error) {
  console.error('❌ 上傳發生網路或伺服器錯誤:', error);
}finally {
    isUploading.value = false;
  }
};

/**
 * 截取 <video> 畫面並壓縮為 Blob
 * @returns {Promise<Blob>}
 */
const captureAndCompress = () => {
  return new Promise((resolve) => {
    const video = videoRef.value;
    const canvas = document.createElement('canvas');
    
    // 保持與視頻流原始比例一致
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    
    const ctx = canvas.getContext('2d');
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    
    // 壓縮為 JPEG，品質 0.5 (約可將 640x480 的圖壓至 20-40KB)
    canvas.toBlob((blob) => {
      resolve(blob);
    }, 'image/jpeg', 0.5);
  });
};


// 狀態管理
const videoRef = ref(null);
const appState = ref('loading'); 
const loadingText = ref('初始化中...');
const currentStatus = ref('系統準備中...');
const isMinimized = ref(false);

// Worker 與 Canvas
let aiWorker = null;
const inferenceCanvas = document.createElement('canvas');
inferenceCanvas.width = 320;
inferenceCanvas.height = 240;
const inferenceCtx = inferenceCanvas.getContext('2d', { willReadFrequently: true });

// 檢測迴圈與心跳計數
let isDetecting = false;
let detectIntervalId = null;
let heartbeatIntervalId = null;

// 統計數據 (心跳包用)
let totalChecks = 0;
let anomalyChecks = 0;

const initializeSystem = async () => {
  appState.value = 'loading';
  loadingText.value = '開啟攝像頭中...';
  
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false });
    videoRef.value.srcObject = stream;
    await new Promise(resolve => { videoRef.value.onloadedmetadata = resolve; });
    videoRef.value.play();

    loadingText.value = '啟動 AI 引擎...';
    
    // 初始化 Web Worker
    aiWorker = new Worker(new URL('../workers/ai.worker.js', import.meta.url), { type: 'module' });
    
    aiWorker.onmessage = handleWorkerMessage;
    
    // 通知 Worker 加載模型 (傳入 public 目錄下的 models 路徑)
    aiWorker.postMessage({ type: 'INIT', payload: { modelPath: '/models' } });

  } catch (error) {
    alert('攝像頭權限被拒絕或加載失敗！');
  }
};

// 處理 Worker 傳回的訊息
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
    console.log(`無感校準完成，基準線: ${payload}`);
    currentStatus.value = '正常：單人端正作答中';
    startHeartbeat();
  }
  else if (type === 'RESULT') {
    const { status, isAnomaly } = payload;
    
    // 如果狀態從正常變成異常，觸發截圖並上傳給後端
    if (isAnomaly && !currentStatus.value.includes('異常')) {
      uploadEvidenceScreenshot(status); // 原本的邏輯
      
      // 👇 新增這行：觸發上傳到後端資料庫的邏輯
      handleAnomaly(status); 
    }

    currentStatus.value = status;
    isDetecting = false;
    totalChecks++;
    if (isAnomaly) anomalyChecks++;
  }
  else if (type === 'ERROR') {
    console.error('❌ Worker 內部發生錯誤:', payload);
    alert('AI 初始化失敗...\n錯誤訊息: ' + payload);
    loadingText.value = '載入失敗';
    isDetecting = false;
  }
};

const startExam = () => {
  appState.value = 'exam';
  aiWorker.postMessage({ type: 'START_CALIBRATION' });
  
  // 啟動主線程的抽幀迴圈 (每秒抽 1-2 幀傳給 Worker)
  detectIntervalId = setInterval(sendFrameToWorker, 1000); 
};

// 抽取畫面並交給 Worker
const sendFrameToWorker = () => {
  if (isDetecting || appState.value !== 'exam') return;
  
  const videoEl = videoRef.value;
  if (!videoEl || videoEl.readyState < 2) return;

  isDetecting = true;
  // 畫到隱藏的 Canvas 上
  inferenceCtx.drawImage(videoEl, 0, 0, inferenceCanvas.width, inferenceCanvas.height);
  // 提取 ImageData (不傳遞 DOM 對象，Worker 可以直接處理 Uint8ClampedArray)
  const imageData = inferenceCtx.getImageData(0, 0, inferenceCanvas.width, inferenceCanvas.height);
  
  aiWorker.postMessage({ 
    type: 'DETECT', 
    payload: { imageData, width: inferenceCanvas.width, height: inferenceCanvas.height } 
  });
};

// 【通信層】發送心跳包
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
      // 替換為你的後端 API
      // await fetch('/api/exam/heartbeat', { method: 'POST', body: JSON.stringify(payload) });
      console.log('💓 發送心跳包:', payload);
      
      // 發送後重置統計
      totalChecks = 0;
      anomalyChecks = 0;
    } catch (err) {
      console.error('心跳包發送失敗', err);
    }
  }, 10000); // 每 10 秒發送一次
};

// 【通信層】上傳作弊證據
const uploadEvidenceScreenshot = async (reason) => {
  // 將 Canvas 壓縮為 JPEG 格式 (質量 0.6) 以減少頻寬消耗
  const base64Image = inferenceCanvas.toDataURL('image/jpeg', 0.6);
  
  const payload = {
    timestamp: Date.now(),
    reason: reason,
    image: base64Image
  };

  try {
    // 替換為你的後端 API
    // await fetch('/api/exam/evidence', { method: 'POST', body: JSON.stringify(payload) });
    console.log('📸 檢測到異常，上傳證據截圖!', reason);
  } catch (err) {
    console.error('證據上傳失敗', err);
  }
};

const statusClass = computed(() => {
  if (currentStatus.value.includes('異常')) return 'error';
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
/* 預設樣式：考場右下角固定的小視窗 */
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

/* 縮小模式 */
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