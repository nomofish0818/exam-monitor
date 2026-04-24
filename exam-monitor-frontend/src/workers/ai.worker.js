// ==========================================
// 1. 伪造浏览器 DOM 环境 
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

// 状态变量
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
  
  // 1. 左右转头检测
  const leftDistance = noseTip.x - jawOutline[0].x;
  const rightDistance = rightEdge.x - noseTip.x;
  const horizontalRatio = leftDistance / rightDistance;
  if (horizontalRatio > 1.8 || horizontalRatio < 0.55) return '左顾右盼'; 

  // 2. 微低头检测：原本的比例公式 (针对 15~45 度)
  const currentVerticalRatio = calculateVerticalRatio(landmarks);
  const isSlightlyDown = (currentVerticalRatio / personalBaselineRatio) > 1.33;
  //console.info(currentVerticalRatio/personalBaselineRatio);

  // 3. 严重低头检测：五官垂直压缩率 (针对 45~75 度)
  const leftEye = landmarks.getLeftEye();
  const rightEye = landmarks.getRightEye();
  const mouth = landmarks.getMouth();

  // 取眼睛的平均 Y 坐标
  const eyeCenterY = (leftEye.reduce((sum, pt) => sum + pt.y, 0) / leftEye.length + 
                      rightEye.reduce((sum, pt) => sum + pt.y, 0) / rightEye.length) / 2;
  // 取嘴巴的平均 Y 坐标
  const mouthCenterY = mouth.reduce((sum, pt) => sum + pt.y, 0) / mouth.length;
  
  // 计算五官占整体脸框高度的比例
  const faceFeaturesHeight = mouthCenterY - eyeCenterY;
  const featureToBoxRatio = faceFeaturesHeight / box.height;

  // 正常平视时，比例约为 0.35 ~ 0.45。严重低头时，比例会被压缩到 0.22 以下
  const isHeavilyDown = featureToBoxRatio < 0.22;

  if (isSlightlyDown || isHeavilyDown) {
    return '低头';
  }

  return '正常';
};

// 监听主线程消息
self.onmessage = async (e) => {
  const { type, payload } = e.data;

  if (type === 'INIT') {
    try {
      faceapi = await import('face-api.js');
      
      // ==========================================
      // 2. 强制手动注入环境，取代 monkeyPatch
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

      // 开始加载模型
      await Promise.all([
        faceapi.nets.ssdMobilenetv1.loadFromUri(payload.modelPath),
        faceapi.nets.faceLandmark68Net.loadFromUri(payload.modelPath)
      ]);
      
      // WebGL 预热 (可选)
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
    // 3 秒后自动结束校准
    setTimeout(() => {
      isSilentlyCalibrating = false;
      if (tempRatios.length > 0) {
        personalBaselineRatio = tempRatios.reduce((a, b) => a + b, 0) / tempRatios.length;
      }
      self.postMessage({ type: 'CALIBRATION_DONE', payload: personalBaselineRatio });
    }, 3000);
  }

  if (type === 'DETECT' && modelsLoaded) {
    const { imageData, width, height } = payload; // ImageData 对象
    
    try {
      // 将 ImageData 转换为 Tensor 给 face-api 处理 (避免 Worker 中 Canvas 兼容性问题)
      const tensor = faceapi.tf.browser.fromPixels(imageData);
      const options = new faceapi.SsdMobilenetv1Options({ minConfidence: 0.5 });
      const detections = await faceapi.detectAllFaces(tensor, options).withFaceLandmarks();
      faceapi.tf.dispose(tensor); // 释放内存非常重要

      //不管有没有抓到脸，都一律回传 STATUS，不要让它跑到下面的异常判断去
      if (isSilentlyCalibrating) {
        if (detections.length === 1) {
          tempRatios.push(calculateVerticalRatio(detections[0].landmarks));
        }
        self.postMessage({ type: 'STATUS', payload: '环境适应中，请正视屏幕保持端正...' });
        return; 
      }

      let status = '正常：单人端正作答中';
      let isAnomaly = false;

      if (detections.length === 0) {
        status = '异常：未检测到人脸 (或被遮挡)！';
        isAnomaly = true;
      } else if (detections.length > 1) {
        status = '异常：检测到多张人脸！';
        isAnomaly = true;
      } else {
        const detection = detections[0];
        const box = detection.detection.box; // 取得识别框
        if (isFaceTooFar(detection.detection.box, width, height)) {
          status = '异常：人脸距离屏幕过远，请靠近摄像头！';
          isAnomaly = true;
        } else {
          const poseResult = checkHeadPose(detection.landmarks, box);
          if (poseResult !== '正常') {
            status = `异常：${poseResult === '低头' ? '疑似低头看小抄！' : '视线偏移/左顾右盼！'}`;
            isAnomaly = true;
          }
        }
      }

      self.postMessage({ type: 'RESULT', payload: { status, isAnomaly } });
    } catch (error) {
      console.error('Worker detection error:', error);
      // 防呆：当 TensorFlow 底层处理某张图片崩溃时，通知主线程释放锁定
      self.postMessage({ type: 'ERROR', payload: error.stack || error.message });
    }
  }
};