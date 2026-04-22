package com.exam.monitor.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtils {

    // 签名密钥（生产环境建议从配置文件读取）
    private static final String SECRET_STR = "your-secure-and-long-enough-secret-key-for-exam-monitor-system";
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(SECRET_STR.getBytes());

    // 过期时间：24小时
    private static final long EXPIRE_MS = 24 * 60 * 60 * 1000;

    /**
     * 生成 Token
     */
    public String createToken(Long userId, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + EXPIRE_MS);

        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(SECRET_KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析 Token
     */
    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(SECRET_KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}