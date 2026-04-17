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
@ServerEndpoint("/ws/monitor/{examId}/{role}/{userId}") // 更新了路徑，加入了 examId
public class ExamMonitorWebSocket {

    // 1. 用於 P2P（發送給特定學生/老師）
    private static final ConcurrentHashMap<String, Session> USER_SESSION_POOL = new ConcurrentHashMap<>();

    // 2. 用於考場廣播（Key 是 examId，Value 是該考場所有老師的 Session）
    private static final ConcurrentHashMap<String, CopyOnWriteArraySet<Session>> EXAM_TEACHER_POOL = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("examId") String examId, @PathParam("role") String role, @PathParam("userId") Long userId) {
        String sessionKey = role + "_" + userId;
        USER_SESSION_POOL.put(sessionKey, session);

        // 如果是老師上線，加入考場廣播池
        if ("teacher".equalsIgnoreCase(role)) {
            EXAM_TEACHER_POOL.computeIfAbsent(examId, k -> new CopyOnWriteArraySet<>()).add(session);
        }
        log.info("【WebSocket】用戶上線: {}, 考場: {}", sessionKey, examId);
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
     * 發送給特定用戶 (警告/踢人用)
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
     * 【關鍵修復】發送給考場內的所有老師 (異常推送用)
     * 這裡的方法名要跟 ServiceImpl 裡面調用的一致
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