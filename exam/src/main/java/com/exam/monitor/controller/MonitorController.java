package com.exam.monitor.controller;

import com.exam.monitor.service.MonitorRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    @Autowired
    private MonitorRecordService monitorRecordService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadScreenshot(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") Long studentId,  // 对应前端 append 的 'userId'
            @RequestParam("examId") Long examId,     // 对应前端 append 的 'examId'
            @RequestParam("type") String abnormalType // 对应前端 append 的 'type'
    ) {
        Map<String, Object> result = new HashMap<>();

        try {
            boolean isSaved = monitorRecordService.saveAbnormalRecord(file, studentId, examId, abnormalType);

            if (isSaved) {
                result.put("code", 200);
                result.put("message", "异常记录上传成功");
                // 未来这里可以加入 WebSocket 发送讯息给教师端的逻辑
                return ResponseEntity.ok(result);
            } else {
                result.put("code", 500);
                result.put("message", "数据库写入失败");
                return ResponseEntity.status(500).body(result);
            }
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "服务器错误: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }
}