package com.cardbattle.util;

import com.cardbattle.common.BizException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 卡组分享码(论文可写: 卡组序列化与校验位设计)
 * ---------------------------------------------------------------
 * 编码格式: CB1 + [5 组为一节, 用 "." 分隔便于抄写] + 1 位校验
 *   例: CB1.01A2b3C4d5.6E7f8G9h0.K   ("." 不参与编码, 解析时自动忽略)
 * 每个卡种占 3 个字符, 承载 18 bit: 高12位=卡牌ID(1~4095), 低6位=张数(1~63)
 * 校验位 = 所有分组值按 (sum*31+v) & 63 计算, 可拦截手滑漏字符 / 多字符 / 抄错
 * ---------------------------------------------------------------
 * 大小写敏感, 字符集为 64 个 URL 安全字符(0-9 A-Z a-z - _)
 */
public final class DeckCodeUtil {

    /** 64 进制字符表(URL 安全) */
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_";
    private static final String PREFIX = "CB1";
    private static final int MAX_ID = 4095;      // 12 bit
    private static final int MAX_CNT = 63;       // 6 bit
    private static final int GROUP = 5;          // 每 5 组插一个 '-' 便于阅读/抄写

    private DeckCodeUtil() { }

    /**
     * 编码: 卡组卡牌 id 列表(含重复) -> 分享码
     */
    public static String encode(List<Long> cardIds) {
        if (cardIds == null || cardIds.isEmpty()) throw new BizException("卡组为空, 无法生成分享码");
        TreeMap<Long, Integer> cnt = new TreeMap<>();     // 按 id 升序, 保证同一卡组生成同一串码
        for (Long id : cardIds) {
            if (id == null || id <= 0) throw new BizException("卡组中存在非法卡牌");
            if (id > MAX_ID) throw new BizException("卡牌ID超出分享码可表示范围: " + id);
            cnt.merge(id, 1, Integer::sum);
        }
        for (Map.Entry<Long, Integer> e : cnt.entrySet()) {
            if (e.getValue() > MAX_CNT) throw new BizException("同名卡牌数量超出分享码可表示范围");
        }

        StringBuilder sb = new StringBuilder(PREFIX);
        int sum = 0, i = 0;
        for (Map.Entry<Long, Integer> e : cnt.entrySet()) {
            long v = e.getKey() * 64L + e.getValue();
            if (i > 0 && i % GROUP == 0) sb.append('.');
            append3(sb, v);
            sum = (sum * 31 + (int) v) & 63;
            i++;
        }
        sb.append(ALPHABET.charAt(sum));
        return sb.toString();
    }

    /**
     * 解码: 分享码 -> (卡牌id -> 张数)。任何异常都抛 BizException(带中文原因)
     */
    public static Map<Long, Integer> decode(String raw) {
        if (raw == null || raw.trim().isEmpty()) throw new BizException("请输入卡组分享码");
        String s = raw.trim();
        // 只保留合法字符, 兼容粘贴时带上的空格 / 换行 / 全角符号
        StringBuilder body = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ALPHABET.indexOf(ch) >= 0) body.append(ch);
        }
        String t = body.toString();
        if (t.length() < 5 || !t.startsWith(PREFIX)) throw new BizException("分享码格式不正确(应以 CB1 开头)");

        String payload = t.substring(3, t.length() - 1);      // 去掉前缀与校验位
        char check = t.charAt(t.length() - 1);
        if (payload.isEmpty() || payload.length() % 3 != 0) throw new BizException("分享码长度不正确, 请检查是否抄漏了字符");

        Map<Long, Integer> r = new TreeMap<>();
        int sum = 0, total = 0;
        for (int i = 0; i < payload.length(); i += 3) {
            long v = 0;
            for (int k = 0; k < 3; k++) {
                int idx = ALPHABET.indexOf(payload.charAt(i + k));
                if (idx < 0) throw new BizException("分享码包含非法字符");
                v = v * 64 + idx;
            }
            sum = (sum * 31 + (int) v) & 63;
            long id = v / 64;
            int c = (int) (v % 64);
            if (id <= 0 || c <= 0) throw new BizException("分享码内容非法");
            r.put(id, r.getOrDefault(id, 0) + c);
            total += c;
        }
        if (ALPHABET.charAt(sum) != check) throw new BizException("分享码校验失败, 请重新复制完整的一串");
        if (total != 30) throw new BizException("卡组张数不是 30 张(当前 " + total + " 张)");

        int kinds = r.size();
        for (Map.Entry<Long, Integer> e : r.entrySet()) {
            if (e.getValue() > MAX_CNT) throw new BizException("同名卡牌数量超出上限");
        }
        if (kinds <= 0) throw new BizException("分享码内容为空");
        return r;
    }

    /** 解码并展开成 30 张卡的 id 列表(按 id 升序, 同 id 相邻) */
    public static List<Long> decodeToList(String code) {
        List<Long> ids = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : decode(code).entrySet()) {
            for (int i = 0; i < e.getValue(); i++) ids.add(e.getKey());
        }
        return ids;
    }

    /** 18 bit 拆成 3 个 6 bit 字符 */
    private static void append3(StringBuilder sb, long v) {
        sb.append(ALPHABET.charAt((int) ((v >> 12) & 63)))
                .append(ALPHABET.charAt((int) ((v >> 6) & 63)))
                .append(ALPHABET.charAt((int) (v & 63)));
    }
}
