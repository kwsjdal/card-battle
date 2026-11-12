package com.cardbattle.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT + 密码工具
 * 自实现 HS256, 无 JAXB 依赖, JDK 11+ 原生 Base64
 */
@Component
public class JwtUtil {

    public static final String PWD_SALT = "cb2026";
    private static final String ALG = "HS256";
    private static final String TYP = "JWT";

    @Value("${cardbattle.jwt.secret}")
    private String secret;

    @Value("${cardbattle.jwt.expire-hours}")
    private long expireHours;

    private final ObjectMapper om = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();

    private String b64url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
    private byte[] b64urlDecode(String s) {
        return Base64.getUrlDecoder().decode(s);
    }
    private byte[] hmac(String data, byte[] key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    /** 生成 token */
    public String generate(Long userId) {
        try {
            Map<String, Object> header = Map.of("alg", ALG, "typ", TYP);
            Map<String, Object> payload = new HashMap<>();
            payload.put("sub", String.valueOf(userId));
            payload.put("iat", System.currentTimeMillis() / 1000);
            payload.put("exp", (System.currentTimeMillis() + expireHours * 3600_000L) / 1000);
            // 随机数(jti): 保证同一秒内多次登录的 token 也各不相同, 单设备互踢才能准确覆盖
            payload.put("jti", Long.toHexString(System.nanoTime()) + Long.toHexString(RANDOM.nextLong()));

            String h = b64url(om.writeValueAsBytes(header));
            String p = b64url(om.writeValueAsBytes(payload));
            String signing = h + "." + p;
            byte[] key = secret.getBytes(StandardCharsets.UTF_8);
            String sig = b64url(hmac(signing, key));
            return signing + "." + sig;
        } catch (Exception e) {
            throw new RuntimeException("JWT生成失败", e);
        }
    }

    /** 解析 token, 非法/过期/签名错返回 null */
    public Long parse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;

            // 1. 签名校验
            String signing = parts[0] + "." + parts[1];
            byte[] key = secret.getBytes(StandardCharsets.UTF_8);
            String expectSig = b64url(hmac(signing, key));
            if (!expectSig.equals(parts[2])) return null;

            // 2. 解析 payload
            Map<?, ?> payload = om.readValue(b64urlDecode(parts[1]), Map.class);
            Object exp = payload.get("exp");
            if (exp != null && ((Number) exp).longValue() * 1000 < System.currentTimeMillis()) return null;

            Object sub = payload.get("sub");
            return sub == null ? null : Long.valueOf(sub.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** 生成每用户随机盐(16 字节 -> 32 位十六进制) */
    public static String generateSalt() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        return hex(b);
    }

    /**
     * 现行方案: SHA-256(随机盐 + 全局盐 + 明文密码)
     * 每用户盐不同 —— 相同密码也会得到不同哈希, 拖库后无法批量反查。
     */
    public static String hashPassword(String rawPwd, String salt) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] bytes = md.digest((salt + PWD_SALT + rawPwd).getBytes(StandardCharsets.UTF_8));
        return hex(bytes);
    }

    /**
     * 旧方案: MD5(全局盐 + 明文密码), 仅用于存量账号迁移。
     * 老账号首次登录成功后会自动升到上面的新方案(见 UserServiceImpl.login)。
     */
    public static String legacyHash(String rawPwd) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] bytes = md.digest((PWD_SALT + rawPwd).getBytes(StandardCharsets.UTF_8));
        return hex(bytes);
    }
}
