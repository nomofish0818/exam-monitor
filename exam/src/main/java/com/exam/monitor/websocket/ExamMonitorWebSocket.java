package com.exam.monitor.websocket;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
@ServerEndpoint("/ws/monitor/{examId}/{role}/{userId}") // 更新了路径，加入了 examId
public class ExamMonitorWebSocket {

    // 1. 用于 P2P（发送给特定学生/老师）
    private static final ConcurrentHashMap<String, Session> USER_SESSION_POOL = new ConcurrentHashMap<>();

    // 2. 用于考场广播（Key 是 examId，Value 是该考场所有老师的 Session）
    private static final ConcurrentHashMap<String, CopyOnWriteArraySet<Session>> EXAM_TEACHER_POOL = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("examId") String examId, @PathParam("role") String role, @PathParam("userId") Long userId) {
        String sessionKey = role + "_" + userId;
        USER_SESSION_POOL.put(sessionKey, session);

        // 如果是老师上线，加入考场广播池
        if ("teacher".equalsIgnoreCase(role)) {
            EXAM_TEACHER_POOL.computeIfAbsent(examId, k -> new CopyOnWriteArraySet<>()).add(session);
        }
        log.info("【WebSocket】用户上线: {}, 考场: {}", sessionKey, examId);
    }

    @OnClose
    public void onClose(Session session, @PathParam("examId") String examId, @PathParam("role") String role, @PathParam("userId") Long userId) {
        String sessionKey = role + "_" + userId;
        USER_SESSION_POOL.remove(sessionKey);

        if ("teacher".equalsIgnoreCase(role)) {
            CopyOnWriteArraySet<Session> teachers = EXAM_TEACHER_POOL.get(examId);
            if (teachers != null) {
                teachers.remove(session);
            }
        }
    }

    /**
     * 发送给特定用户 (警告/踢人用)
     */
    public static boolean sendToUser(String role, Long userId, String message) {
        Session session = USER_SESSION_POOL.get(role + "_" + userId);
        if (session != null && session.isOpen()) {
            session.getAsyncRemote().sendText(message);
            return true;
        }
        return false;
    }

    /**
     * 【关键修复】发送给考场内的所有老师 (异常推送用)
     * 这里的方法名要跟 ServiceImpl 里面调用的一致
     */
    public static void sendToExam(String examId, String message) {
        CopyOnWriteArraySet<Session> teachers = EXAM_TEACHER_POOL.get(examId);
        if (teachers != null) {
            for (Session session : teachers) {
                if (session.isOpen()) {
                    session.getAsyncRemote().sendText(message);
                }
            }
        }
    }
}