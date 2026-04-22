package com.exam.monitor.controller;

import com.exam.monitor.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> loginParams) {
        String userId = loginParams.get("userId");
        String password = loginParams.get("password");

        // TODO: 这里应当调用 UserService 查询数据库并校验密码 (BCrypt)
        // 此处为演示，默认验证通过

        // 生成 Token
        String token = jwtUtils.createToken(Long.valueOf(userId), "student");

        // 写入 Redis (Key, Value, Timeout, Unit)
        String redisKey = "login:token:" + userId;
        redisTemplate.opsForValue().set(redisKey, token, 24, TimeUnit.HOURS);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "登录成功");

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("userId", userId);
        result.put("data", data);

        return result;
    }
}