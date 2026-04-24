package com.exam.monitor.controller;

import com.exam.monitor.utils.JwtUtils;
import com.exam.monitor.entity.SysUser; // 假设你的实体类路径
import com.exam.monitor.mapper.SysUserMapper; // 或者使用 Service
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; // 需引入 Spring Security 依赖
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // 建议使用 Mapper 或 Service 查询数据库
    @Autowired
    private SysUserMapper sysUserMapper;

    // 用于校验 BCrypt 加密密码
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> loginParams) {
        Map<String, Object> result = new HashMap<>();
        String userIdStr = loginParams.get("userId");
        String password = loginParams.get("password");

        // 1. 参数健壮性检查：防止 Long.valueOf(null) 导致 500 错误
        if (userIdStr == null || userIdStr.isEmpty() || password == null) {
            result.put("code", 400);
            result.put("message", "用户ID或密码不能为空");
            return result;
        }

        try {
            Long userId = Long.valueOf(userIdStr);

            // 2. 数据库真实校验
            // 注意：这里根据你的数据库字段，建议通过 username (学号) 或 id 查询
            SysUser user = sysUserMapper.selectById(userId);

            if (user == null) {
                result.put("code", 401);
                result.put("message", "用户不存在");
                return result;
            }

            // 3. BCrypt 密码校验
            // 数据库中的密码是 $2a$10$... 开头的密文
            if (!passwordEncoder.matches(password, user.getPassword())) {
                result.put("code", 401);
                result.put("message", "密码错误");
                return result;
            }

            // 4. 生成 Token
            String token = jwtUtils.createToken(userId, "student");

            // 5. 写入 Redis 并增加异常捕获
            // 如果 Redis 没启动，这里会抛出异常，捕获后返回友好提示而非 500
            try {
                String redisKey = "login:token:" + userId;
                redisTemplate.opsForValue().set(redisKey, token, 24, TimeUnit.HOURS);
            } catch (Exception redisEx) {
                log.error("Redis 连接失败，请检查服务是否启动: {}", redisEx.getMessage());
                result.put("code", 503);
                result.put("message", "服务器缓存服务异常，请联系管理员");
                return result;
            }

            // 6. 组装成功返回数据
            result.put("code", 200);
            result.put("message", "登录成功");
            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("userId", userIdStr);
            data.put("role", user.getRole());
            result.put("data", data);
            return result;

        } catch (NumberFormatException e) {
            result.put("code", 400);
            result.put("message", "用户ID格式不正确");
            return result;
        } catch (Exception e) {
            log.error("登录系统未知异常", e);
            result.put("code", 500);
            result.put("message", "服务器内部错误");
            return result;
        }
    }
}