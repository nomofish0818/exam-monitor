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
import com.fasterxml.jackson.databind.ObjectMapper; // 用于将对象转为 JSON
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class MonitorRecordServiceImpl extends ServiceImpl<MonitorRecordMapper, MonitorRecord> implements MonitorRecordService {

    // 定义存档根目录
    private static final String UPLOAD_DIR = "D:/exam-uploads/";

    // 注入 Jackson 的 ObjectMapper 处理 JSON
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public boolean saveAbnormalRecord(MultipartFile file, Long studentId, Long examId, String abnormalType) {
        if (file.isEmpty()) {
            throw new RuntimeException("上传的截图为空");
        }

        try {
            // 1. 确保目录存在
            File dir = new File(UPLOAD_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 2. 生成 UUID 文件名 (保留原本的扩展名，通常是 .jpg)
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String newFileName = UUID.randomUUID().toString().replace("-", "") + extension;

            // 3. 保存文件到本地
            File dest = new File(UPLOAD_DIR + newFileName);
            file.transferTo(dest);

            // 4. 构建要存入数据库的实体 (保存虚拟路径)
            MonitorRecord record = new MonitorRecord();
            record.setExamId(examId);
            record.setStudentId(studentId);
            record.setAbnormalType(abnormalType);
            // 这里的路径与 WebConfig 映射的路径对应
            record.setScreenshotUrl("/uploads/" + newFileName);
            record.setTriggerTime(LocalDateTime.now());

            // 5. 写入数据库
            boolean isSaved = this.save(record);

            // 6. 如果存档成功，触发 WebSocket 推播给教师
            if (isSaved) {
                pushAlertToTeachers(record);
            }

            return isSaved;

        } catch (IOException e) {
            log.error("保存截图失败", e);
            throw new RuntimeException("截图保存失败");
        }
    }

    /**
     * 构建推播消息并调用 WebSocket
     */
    private void pushAlertToTeachers(MonitorRecord record) {
        try {
            // 建立要推播给前端的 JSON 对象
            ObjectNode alertNode = objectMapper.createObjectNode();
            alertNode.put("type", "NEW_ANOMALY");
            alertNode.put("recordId", record.getId());
            alertNode.put("studentId", record.getStudentId());
            alertNode.put("abnormalType", record.getAbnormalType());
            alertNode.put("screenshotUrl", record.getScreenshotUrl());
            // 将 LocalDateTime 转为字符串
            alertNode.put("triggerTime", record.getTriggerTime().toString());

            String jsonMessage = objectMapper.writeValueAsString(alertNode);

            // 调用我们刚刚写的 WebSocket 静态方法，精准推播给该 examId 的考场
            ExamMonitorWebSocket.sendToExam(String.valueOf(record.getExamId()), jsonMessage);

        } catch (Exception e) {
            log.error("组装 WebSocket 推播消息失败", e);
        }
    }
}