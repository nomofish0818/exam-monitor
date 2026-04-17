package com.exam.monitor.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.exam.monitor.entity.MonitorRecord;
import com.exam.monitor.mapper.MonitorRecordMapper;
import com.exam.monitor.service.MonitorRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;
import com.exam.monitor.websocket.ExamMonitorWebSocket;
import com.fasterxml.jackson.databind.ObjectMapper; // 用於將物件轉為 JSON
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class MonitorRecordServiceImpl extends ServiceImpl<MonitorRecordMapper, MonitorRecord> implements MonitorRecordService {

    // 定義存檔根目錄
    private static final String UPLOAD_DIR = "D:/exam-uploads/";

    // 注入 Jackson 的 ObjectMapper 處理 JSON
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public boolean saveAbnormalRecord(MultipartFile file, Long studentId, Long examId, String abnormalType) {
        if (file.isEmpty()) {
            throw new RuntimeException("上傳的截圖為空");
        }

        try {
            // 1. 確保目錄存在
            File dir = new File(UPLOAD_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 2. 生成 UUID 檔名 (保留原本的副檔名，通常是 .jpg)
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String newFileName = UUID.randomUUID().toString().replace("-", "") + extension;

            // 3. 儲存檔案到本地
            File dest = new File(UPLOAD_DIR + newFileName);
            file.transferTo(dest);

            // 4. 構建要存入資料庫的實體 (保存虛擬路徑)
            MonitorRecord record = new MonitorRecord();
            record.setExamId(examId);
            record.setStudentId(studentId);
            record.setAbnormalType(abnormalType);
            // 這裡的路徑與 WebConfig 映射的路徑對應
            record.setScreenshotUrl("/uploads/" + newFileName);
            record.setTriggerTime(LocalDateTime.now());

            // 5. 寫入資料庫
            boolean isSaved = this.save(record);

            // 6. 如果存檔成功，觸發 WebSocket 推播給教師
            if (isSaved) {
                pushAlertToTeachers(record);
            }

            return isSaved;

        } catch (IOException e) {
            log.error("儲存截圖失敗", e);
            throw new RuntimeException("截圖儲存失敗");
        }
    }

    /**
     * 構建推播訊息並呼叫 WebSocket
     */
    private void pushAlertToTeachers(MonitorRecord record) {
        try {
            // 建立要推播給前端的 JSON 物件
            ObjectNode alertNode = objectMapper.createObjectNode();
            alertNode.put("type", "NEW_ANOMALY");
            alertNode.put("recordId", record.getId());
            alertNode.put("studentId", record.getStudentId());
            alertNode.put("abnormalType", record.getAbnormalType());
            alertNode.put("screenshotUrl", record.getScreenshotUrl());
            // 將 LocalDateTime 轉為字串
            alertNode.put("triggerTime", record.getTriggerTime().toString());

            String jsonMessage = objectMapper.writeValueAsString(alertNode);

            // 呼叫我們剛剛寫的 WebSocket 靜態方法，精準推播給該 examId 的考場
            ExamMonitorWebSocket.sendToExam(String.valueOf(record.getExamId()), jsonMessage);

        } catch (Exception e) {
            log.error("組裝 WebSocket 推播訊息失敗", e);
        }
    }
}