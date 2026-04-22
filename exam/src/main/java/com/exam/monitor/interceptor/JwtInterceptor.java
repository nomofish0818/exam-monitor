package com.exam.monitor.interceptor;

import com.exam.monitor.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 放行 OPTIONS 请求（跨域预检）
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        // 2. 获取请求头中的 Token
        String token = request.getHeader("Authorization");
        if (!StringUtils.hasText(token)) {
            response.setStatus(401);
            return false;
        }

        try {
            // 3. 解析 JWT
            Claims claims = jwtUtils.parseToken(token);
            String userId = claims.getSubject();

            // 4. Redis 校验（Key 格式：login:token:1002）
            String redisKey = "login:token:" + userId;
            String cachedToken = redisTemplate.opsForValue().get(redisKey);

            if (!token.equals(cachedToken)) {
                response.setStatus(401); // Token 已失效（例如用户在别处登录或管理员手动强制下线）
                return false;
            }

            return true;
        } catch (Exception e) {
            response.setStatus(401);
            return false;
        }
    }
}