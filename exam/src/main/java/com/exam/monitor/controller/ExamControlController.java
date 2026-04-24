package com.exam.monitor.controller;

import com.exam.monitor.websocket.ExamMonitorWebSocket;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/exam")
public class ExamControlController {

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 1. 警告学生 API
     * POST /api/exam/warn
     */
    @PostMapping("/warn")
    public ResponseEntity<Map<String, Object>> warnStudent(@RequestBody WarnRequest request) {
        Map<String, Object> response = new HashMap<>();

        try {
            // 构建要发送给学生的 JSON: {"action": "WARN", "msg": "..."}
            ObjectNode jsonNode = objectMapper.createObjectNode();
            jsonNode.put("action", "WARN");
            jsonNode.put("msg", request.getMessage());

            String jsonMessage = objectMapper.writeValueAsString(jsonNode);

            // 呼叫 WebSocket 发送 (指定 role 为 "student")
            boolean isSent = ExamMonitorWebSocket.sendToUser("student", request.getStudentId(), jsonMessage);

            if (isSent) {
                response.put("code", 200);
                response.put("message", "警告已发送给学生");
                return ResponseEntity.ok(response);
            } else {
                response.put("code", 404);
                response.put("message", "学生目前不线上，发送失败");
                return ResponseEntity.status(404).body(response);
            }
        } catch (Exception e) {
            log.error("警告发送异常", e);
            response.put("code", 500);
            response.put("message", "服务器错误");
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 2. 强制交卷 (踢出) API
     * POST /api/exam/terminate
     */
    @PostMapping("/terminate")
    public ResponseEntity<Map<String, Object>> terminateExam(@RequestBody TerminateRequest request) {
        Map<String, Object> response = new HashMap<>();

        try {
            // TODO: 在这里加入数据库业务逻辑 (例如将 monitor_record 或 exam_student 表的状态改为 "强制交卷")
            // examService.forceSubmit(request.getStudentId(), request.getReason());

            // 构建 JSON: {"action": "KICK", "reason": "..."}
            ObjectNode jsonNode = objectMapper.createObjectNode();
            jsonNode.put("action", "KICK");
            jsonNode.put("reason", request.getReason());

            String jsonMessage = objectMapper.writeValueAsString(jsonNode);

            // 通知学生端强制退出
            boolean isSent = ExamMonitorWebSocket.sendToUser("student", request.getStudentId(), jsonMessage);

            response.put("code", 200);
            response.put("message", isSent ? "已成功踢出该学生" : "数据库已更新，但学生不在线上");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("强制交卷异常", e);
            response.put("code", 500);
            response.put("message", "服务器错误");
            return ResponseEntity.status(500).body(response);
        }
    }

    // --- DTO 类别 (用于接收前端的 JSON Request Body) ---

    @Data
    public static class WarnRequest {
        private Long studentId;
        private String message;
    }

    @Data
    public static class TerminateRequest {
        private Long studentId;
        private String reason;
    }
}