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
 * JWT 工具类。
 * 当前项目里它主要承担两件事：
 * 1. user-service 登录成功后生成 token；
 * 2. gateway 对请求 token 做解析和验签。
 *
 * 签名算法使用 HS256，适合单体或单团队维护的对称密钥场景。
 */
public final class JwtUtils {

    /** 对称签名密钥。示例项目中直接写死，生产环境应迁移到安全配置源。 */
    private static final String SECRET = "mySuperSecretKey1234567890mySuperSecretKey-ye94z";
    private static final Key KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    /** 默认 TTL。这里乘了 10000，更偏向本地联调而不是严格的 30 分钟。 */
    public static final long DEFAULT_TTL_MILLIS = 10000 * 30 * 60 * 1000L; // 方便测试

    private JwtUtils() {}

    /** 使用默认 TTL 生成 token。 */
    public static String generateToken(Map<String, Object> claims) {
        return generateToken(claims, DEFAULT_TTL_MILLIS);
    }

    /** 使用自定义 TTL 生成 token。 */
    public static String generateToken(Map<String, Object> claims, long ttlMillis) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + ttlMillis))
                .signWith(KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    /** 解析并校验 token；验签失败、格式非法或过期时统一返回 null。 */
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

    /** 判断 token 是否进入“即将过期”窗口。 */
    public static boolean isExpiringSoon(Claims claims, long windowMillis) {
        if (claims == null || claims.getExpiration() == null) return true;
        long left = claims.getExpiration().getTime() - System.currentTimeMillis();
        return left <= windowMillis;
    }

    /**
     * 若 token 将在 windowMillis 内过期，则基于原 claims 生成一个新 token。
     * 返回 null 表示无需刷新。
     */
    public static String refreshIfNeeded(Claims claims, long windowMillis, long newTtlMillis) {
        if (!isExpiringSoon(claims, windowMillis)) return null;
        // 仅拷贝业务自定义字段，标准字段由 JJWT 重新生成。
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
