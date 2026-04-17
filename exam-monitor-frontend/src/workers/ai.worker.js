// ==========================================
// 1. 偽造瀏覽器 DOM 環境 
// ==========================================
globalThis.window = globalThis;
globalThis.document = {
  createElement: (tagName) => {
    if (tagName === 'canvas') return new OffscreenCanvas(320, 240);
    return {};
  }
};
globalThis.HTMLVideoElement = class {};
globalThis.HTMLImageElement = class {};
globalThis.HTMLCanvasElement = class {};
globalThis.Image = class {};

let faceapi;

// 狀態變數
let isSilentlyCalibrating = false;
let personalBaselineRatio = 1.0;
let tempRatios = [];
let modelsLoaded = false;

const calculateVerticalRatio = (landmarks) => {
  const jawOutline = landmarks.getJawOutline(); 
  const noseFeatures = landmarks.getNose();     
  const noseTip = noseFeatures[3];      
  const noseBridge = noseFeatures[0];   
  const chin = jawOutline[8];           
  const topToNoseDist = noseTip.y - noseBridge.y;
  const noseToChinDist = chin.y - noseTip.y;
  return topToNoseDist / noseToChinDist;
};

const isFaceTooFar = (box, width, height) => {
  const faceArea = box.width * box.height;
  const canvasArea = width * height;
  return (faceArea / canvasArea) < 0.07; 
};

const checkHeadPose = (landmarks, box) => {
  const jawOutline = landmarks.getJawOutline(); 
  const noseFeatures = landmarks.getNose();     
  const noseTip = noseFeatures[3];      
  const rightEdge = jawOutline[16];     
  
  // 1. 左右轉頭檢測
  const leftDistance = noseTip.x - jawOutline[0].x;
  const rightDistance = rightEdge.x - noseTip.x;
  const horizontalRatio = leftDistance / rightDistance;
  if (horizontalRatio > 1.8 || horizontalRatio < 0.55) return '左顧右盼'; 

  // 2. 微低頭檢測：原本的比例公式 (針對 15~45 度)
  const currentVerticalRatio = calculateVerticalRatio(landmarks);
  const isSlightlyDown = (currentVerticalRatio / personalBaselineRatio) > 1.33;
  //console.info(currentVerticalRatio/personalBaselineRatio);

  // 3. 嚴重低頭檢測：五官垂直壓縮率 (針對 45~75 度)
  const leftEye = landmarks.getLeftEye();
  const rightEye = landmarks.getRightEye();
  const mouth = landmarks.getMouth();

  // 取眼睛的平均 Y 座標
  const eyeCenterY = (leftEye.reduce((sum, pt) => sum + pt.y, 0) / leftEye.length + 
                      rightEye.reduce((sum, pt) => sum + pt.y, 0) / rightEye.length) / 2;
  // 取嘴巴的平均 Y 座標
  const mouthCenterY = mouth.reduce((sum, pt) => sum + pt.y, 0) / mouth.length;
  
  // 計算五官佔整體臉框高度的比例
  const faceFeaturesHeight = mouthCenterY - eyeCenterY;
  const featureToBoxRatio = faceFeaturesHeight / box.height;

  // 正常平視時，比例約為 0.35 ~ 0.45。嚴重低頭時，比例會被壓縮到 0.22 以下
  const isHeavilyDown = featureToBoxRatio < 0.22;

  if (isSlightlyDown || isHeavilyDown) {
    return '低頭';
  }

  return '正常';
};

// 監聽主線程消息
self.onmessage = async (e) => {
  const { type, payload } = e.data;

  if (type === 'INIT') {
    try {
      faceapi = await import('face-api.js');
      
      // ==========================================
      // 2. 強制手動注入環境，取代 monkeyPatch
      // ==========================================
      faceapi.env.setEnv({
        Canvas: OffscreenCanvas,
        CanvasRenderingContext2D: OffscreenCanvasRenderingContext2D,
        Image: globalThis.Image,
        ImageData: globalThis.ImageData,
        Video: globalThis.HTMLVideoElement,
        createCanvasElement: () => new OffscreenCanvas(320, 240),
        createImageElement: () => ({}),
        fetch: globalThis.fetch.bind(globalThis)
      });

      // 開始載入模型
      await Promise.all([
        faceapi.nets.ssdMobilenetv1.loadFromUri(payload.modelPath),
        faceapi.nets.faceLandmark68Net.loadFromUri(payload.modelPath)
      ]);
      
      // WebGL 預熱 (可選)
      const blankTensor = faceapi.tf.zeros([240, 320, 3]);
      await faceapi.detectAllFaces(blankTensor, new faceapi.SsdMobilenetv1Options({ minConfidence: 0.1 })).withFaceLandmarks();
      faceapi.tf.dispose(blankTensor);

      modelsLoaded = true;
      self.postMessage({ type: 'INIT_DONE' });
    } catch (error) {
      self.postMessage({ type: 'ERROR', payload: error.stack || error.message });
    }
  }

  if (type === 'START_CALIBRATION') {
    isSilentlyCalibrating = true;
    tempRatios = [];
    // 3 秒後自動結束校準
    setTimeout(() => {
      isSilentlyCalibrating = false;
      if (tempRatios.length > 0) {
        personalBaselineRatio = tempRatios.reduce((a, b) => a + b, 0) / tempRatios.length;
      }
      self.postMessage({ type: 'CALIBRATION_DONE', payload: personalBaselineRatio });
    }, 3000);
  }

  if (type === 'DETECT' && modelsLoaded) {
    const { imageData, width, height } = payload; // ImageData 對象
    
    try {
      // 將 ImageData 轉換為 Tensor 給 face-api 處理 (避免 Worker 中 Canvas 兼容性問題)
      const tensor = faceapi.tf.browser.fromPixels(imageData);
      const options = new faceapi.SsdMobilenetv1Options({ minConfidence: 0.5 });
      const detections = await faceapi.detectAllFaces(tensor, options).withFaceLandmarks();
      faceapi.tf.dispose(tensor); // 釋放記憶體非常重要

      //不管有沒有抓到臉，都一律回傳 STATUS，不要讓它跑到下面的異常判斷去
      if (isSilentlyCalibrating) {
        if (detections.length === 1) {
          tempRatios.push(calculateVerticalRatio(detections[0].landmarks));
        }
        self.postMessage({ type: 'STATUS', payload: '環境適應中，請正視螢幕保持端正...' });
        return; 
      }

      let status = '正常：單人端正作答中';
      let isAnomaly = false;

      if (detections.length === 0) {
        status = '異常：未檢測到人臉 (或被遮擋)！';
        isAnomaly = true;
      } else if (detections.length > 1) {
        status = '異常：檢測到多張人臉！';
        isAnomaly = true;
      } else {
        const detection = detections[0];
        const box = detection.detection.box; // 取得識別框
        if (isFaceTooFar(detection.detection.box, width, height)) {
          status = '異常：人臉距離螢幕過遠，請靠近攝像頭！';
          isAnomaly = true;
        } else {
          const poseResult = checkHeadPose(detection.landmarks, box);
          if (poseResult !== '正常') {
            status = `異常：${poseResult === '低頭' ? '疑似低頭看小抄！' : '視線偏移/左顧右盼！'}`;
            isAnomaly = true;
          }
        }
      }

      self.postMessage({ type: 'RESULT', payload: { status, isAnomaly } });
    } catch (error) {
      console.error('Worker detection error:', error);
      // 防呆：當 TensorFlow 底層處理某張圖片崩潰時，通知主線程釋放鎖定
      self.postMessage({ type: 'ERROR', payload: error.stack || error.message });
    }
  }
};