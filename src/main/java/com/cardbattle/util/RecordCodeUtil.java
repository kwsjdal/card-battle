package com.cardbattle.util;

import com.cardbattle.common.BizException;

/**
 * 战报分享码(与卡组分享码同一套思路: 定长 + 校验位)
 * ---------------------------------------------------------------
 * 编码格式: RB1 + 4 个 64 进制字符(承载 24 bit 的对局编号) + 1 位校验
 *   例: RB1A3f9K7
 * 校验位 = 4 个载荷字符按 (sum*31+v) & 63 累加, 可拦截抄漏 / 抄错 / 字符换位。
 * ---------------------------------------------------------------
 * 拿到码的人可以看到该局的**脱敏回放**(只含战斗日志, 不含任何一方手牌),
 * 因此比直接暴露自增 id 更可控: 码可以随手分享, 也随时能靠校验位识别出无效码。
 */
public final class RecordCodeUtil {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_";
    private static final String PREFIX = "RB1";
    private static final int BODY_LEN = 4;             // 4 × 6 bit = 24 bit
    private static final long MAX_ID = (1L << 24) - 1; // 16,777,215

    private RecordCodeUtil() { }

    /** 对局编号 -> 分享码 */
    public static String encode(long id) {
        if (id <= 0) throw new BizException("对局编号非法, 无法生成分享码");
        if (id > MAX_ID) throw new BizException("对局编号超出分享码可表示范围: " + id);

        StringBuilder sb = new StringBuilder(PREFIX);
        sb.append(ALPHABET.charAt((int) ((id >> 18) & 63)))
                .append(ALPHABET.charAt((int) ((id >> 12) & 63)))
                .append(ALPHABET.charAt((int) ((id >> 6) & 63)))
                .append(ALPHABET.charAt((int) (id & 63)));

        int sum = 0;
        for (int i = PREFIX.length(); i < sb.length(); i++) {
            sum = (sum * 31 + ALPHABET.indexOf(sb.charAt(i))) & 63;
        }
        sb.append(ALPHABET.charAt(sum));
        return sb.toString();
    }

    /** 分享码 -> 对局编号。任何异常都抛 BizException(带中文原因) */
    public static long decode(String raw) {
        if (raw == null || raw.trim().isEmpty()) throw new BizException("请输入战报分享码");
        // 兼容粘贴时带上的空格 / 换行 / 全角符号: 只保留合法字符
        StringBuilder body = new StringBuilder();
        for (char ch : raw.trim().toCharArray()) {
            if (ALPHABET.indexOf(ch) >= 0) body.append(ch);
        }
        String t = body.toString();
        if (t.length() < PREFIX.length() + BODY_LEN + 1 || !t.startsWith(PREFIX)) {
            throw new BizException("战报码格式不正确(应以 RB1 开头)");
        }
        String payload = t.substring(PREFIX.length(), t.length() - 1);
        char check = t.charAt(t.length() - 1);
        if (payload.length() != BODY_LEN) {
            throw new BizException("战报码长度不正确, 请检查是否抄漏了字符");
        }

        long id = 0;
        int sum = 0;
        for (int i = 0; i < payload.length(); i++) {
            int v = ALPHABET.indexOf(payload.charAt(i));
            if (v < 0) throw new BizException("战报码包含非法字符");
            id = id * 64 + v;
            sum = (sum * 31 + v) & 63;
        }
        if (ALPHABET.charAt(sum) != check) {
            throw new BizException("战报码校验失败, 请重新复制完整的一串");
        }
        if (id <= 0) throw new BizException("战报码内容非法");
        return id;
    }
}
