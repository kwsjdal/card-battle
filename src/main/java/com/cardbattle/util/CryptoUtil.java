package com.cardbattle.util;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 密码可逆加密(AES-128-CBC + 随机 IV, Base64 输出)。
 *
 * 用途说明(重要):
 *   登录校验仍然走 JwtUtil.hashPassword(随机盐 + SHA-256), 不可逆;
 *   这里额外保存一份可逆密文, 只为满足"管理员可以查看玩家密码"的需求。
 *   ⚠️ 生产系统不应该做这个功能 —— 一旦泄露密钥等同于明文存密码。
 *   毕业设计演示场景保留, 如需更合规可改为"管理员只能重置不能查看"。
 */
public final class CryptoUtil {

    /** 密钥素材(16 字节 = AES-128); 演示项目内联, 生产应放到 KMS / 环境变量 */
    private static final String SECRET = "card_battle_pwd_key";
    private static final String ALGO = "AES/CBC/PKCS5Padding";

    private CryptoUtil() { }

    private static SecretKeySpec key() {
        byte[] k = new byte[16];
        byte[] raw = SECRET.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(raw, 0, k, 0, Math.min(raw.length, 16));
        return new SecretKeySpec(k, "AES");
    }

    /** 加密: 随机 IV 拼在密文前 16 字节, 整体 Base64 */
    public static String encrypt(String plain) {
        if (plain == null) return null;
        try {
            Cipher c = Cipher.getInstance(ALGO);
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            c.init(Cipher.ENCRYPT_MODE, key(), new IvParameterSpec(iv));
            byte[] enc = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(enc, 0, out, iv.length, enc.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("密码加密失败", e);
        }
    }

    /** 解密; 密文为空或异常时返回空串(管理员端据此提示"无法还原") */
    public static String decrypt(String b64) {
        if (b64 == null || b64.isEmpty()) return "";
        try {
            byte[] all = Base64.getDecoder().decode(b64);
            if (all.length <= 16) return "";
            byte[] iv = Arrays.copyOfRange(all, 0, 16);
            byte[] enc = Arrays.copyOfRange(all, 16, all.length);
            Cipher c = Cipher.getInstance(ALGO);
            c.init(Cipher.DECRYPT_MODE, key(), new IvParameterSpec(iv));
            return new String(c.doFinal(enc), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
