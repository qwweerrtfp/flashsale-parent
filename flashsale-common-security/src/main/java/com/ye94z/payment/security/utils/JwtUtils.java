package com.ye94z.payment.security.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类（HS256）
 * - 默认过期时间：30分钟
 * - 支持自定义TTL
 * - 提供“将要过期则刷新”的便捷方法（滑动续期由上层按需决定是否下发新token）
 */
public final class JwtUtils {

    /** 256-bit 以上的密钥，生产建议来自配置中心/环境变量 */
    private static final String SECRET = "mySuperSecretKey1234567890mySuperSecretKey-ye94z";
    private static final Key KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    /** 默认 30 分钟 */
    public static final long DEFAULT_TTL_MILLIS = 10000 * 30 * 60 * 1000L; // 方便测试

    private JwtUtils() {}

    /** 生成token（默认TTL=30min） */
    public static String generateToken(Map<String, Object> claims) {
        return generateToken(claims, DEFAULT_TTL_MILLIS);
    }

    /** 生成token（自定义TTL） */
    public static String generateToken(Map<String, Object> claims, long ttlMillis) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + ttlMillis))
                .signWith(KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    /** 解析并验证token，失败或过期返回null */
    public static Claims parseToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(KEY)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException e) {
            return null;
        }
    }

    /** 距离过期是否在 windowMillis 以内（用于滑动续期判断） */
    public static boolean isExpiringSoon(Claims claims, long windowMillis) {
        if (claims == null || claims.getExpiration() == null) return true;
        long left = claims.getExpiration().getTime() - System.currentTimeMillis();
        return left <= windowMillis;
    }

    /**
     * 若 token 将在 windowMillis 内过期，则基于原claims生成一个新token（复制自定义字段）。
     * 返回 null 表示无需刷新。
     */
    public static String refreshIfNeeded(Claims claims, long windowMillis, long newTtlMillis) {
        if (!isExpiringSoon(claims, windowMillis)) return null;
        // 仅拷贝自定义claims（标准字段由JJWT内部重新设置）
        Map<String, Object> data = new HashMap<>();
        for (Map.Entry<String, Object> e : claims.entrySet()) {
            String k = e.getKey();
            if (!"exp".equals(k) && !"iat".equals(k)) {
                data.put(k, e.getValue());
            }
        }
        return generateToken(data, newTtlMillis);
    }
}