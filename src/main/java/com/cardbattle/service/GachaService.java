package com.cardbattle.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cardbattle.common.BizException;
import com.cardbattle.entity.*;
import com.cardbattle.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

/** 抽卡: 稀有度加权 -> 随机卡 -> 已拥有则回收金币 */
@Service
@RequiredArgsConstructor
public class GachaService {

    public static final int COST_ONE = 100;       // 单抽(金币)
    public static final int COST_TEN = 900;       // 十连(九折, 金币)
    public static final int CRYSTAL_SURCHARGE = 100; // 水晶抽卡每抽比金币贵100
    public static final int DUP_REFUND = 20;      // 重复卡回收(金币)

    /** 保底: 10 抽内必出「稀有」及以上(稀有度>=3), 40 抽内必出「传说」(稀有度=4) */
    public static final int PITY_RARE = 10;
    public static final int PITY_LEGEND = 40;
    private static final String PITY_RARE_KEY = "gacha:pity:rare:";
    private static final String PITY_LEGEND_KEY = "gacha:pity:legend:";

    private final UserMapper userMapper;
    private final UserCardMapper userCardMapper;
    private final GachaLogMapper gachaLogMapper;
    private final CardCache cardCache;
    private final BonusService bonusService;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();
    private final org.springframework.data.redis.core.RedisTemplate<String, Object> redis;

    /** 卡池: 全部卡 + 是否已拥有 */
    public Map<String, Object> pool(Long userId) {
        // 静态字段直接复用缓存视图, 只补 per-user 的 owned(免去 66×10 次 put)
        List<Map<String, Object>> staticViews = cardCache.staticViews();
        Set<Long> owned = ownedIds(userId);
        List<Map<String, Object>> cards = new ArrayList<>(staticViews.size());
        for (Map<String, Object> v : staticViews) {
            Map<String, Object> m = new HashMap<>(v);
            m.put("owned", owned.contains(v.get("id")));
            cards.add(m);
        }
        Map<String, Object> r = new HashMap<>();
        r.put("cards", cards);
        r.put("ownedCount", owned.size());
        r.put("totalCount", cards.size());
        r.put("costOne", COST_ONE);
        r.put("costTen", COST_TEN);
        r.put("crystalOne", COST_ONE + CRYSTAL_SURCHARGE);
        r.put("crystalTen", COST_TEN + 10 * CRYSTAL_SURCHARGE);
        // 保底进度(供前端展示"距离保底还剩 N 抽")
        r.put("pityRare", PITY_RARE - pity(userId, PITY_RARE_KEY));
        r.put("pityLegend", PITY_LEGEND - pity(userId, PITY_LEGEND_KEY));
        r.put("pityRareMax", PITY_RARE);
        r.put("pityLegendMax", PITY_LEGEND);
        User me = userMapper.selectById(userId);
        r.put("balance", me == null ? 0 : me.getBalance());
        r.put("crystal", me == null || me.getCrystal() == null ? 0 : me.getCrystal());
        return r;
    }

    public Set<Long> ownedIds(Long userId) {
        return userCardMapper.selectList(new LambdaQueryWrapper<UserCard>().eq(UserCard::getUserId, userId))
                .stream().map(UserCard::getCardId).collect(Collectors.toSet());
    }

    /** 抽卡: times=1 或 10, currency="gold"(默认) | "crystal" */
    @Transactional
    public Map<String, Object> pull(Long userId, int times) { return pull(userId, times, "gold"); }

    @Transactional
    public Map<String, Object> pull(Long userId, int times, String currency) {
        if (times != 1 && times != 10) throw new BizException("抽卡次数只能是1或10");
        boolean useCrystal = "crystal".equals(currency);
        int cost = times == 1 ? COST_ONE : COST_TEN;
        if (useCrystal) cost += times * CRYSTAL_SURCHARGE;

        User u = userMapper.selectById(userId);
        if (u == null) throw new BizException("用户不存在");
        if (useCrystal) {
            int have = u.getCrystal() == null ? 0 : u.getCrystal();
            if (have < cost) throw new BizException("水晶不足, 可通过每日签到与任务获得");
        } else if (u.getBalance() < cost) {
            throw new BizException("金币不足, 请先充值");
        }

        List<Card> all = cardCache.all();
        if (all.isEmpty()) throw new BizException("卡池为空");
        Map<Integer, List<Card>> byRarity = cardCache.byRarity();   // 分组已缓存, 抽卡热路径不再重算
        Set<Long> owned = ownedIds(userId);

        List<Map<String, Object>> results = new ArrayList<>();
        int refund = 0, newlyOwned = 0;
        // 跨抽卡次数累计的保底计数(单抽与十连共用同一进度)
        int rareCnt = pity(userId, PITY_RARE_KEY);
        int legendCnt = pity(userId, PITY_LEGEND_KEY);

        for (int i = 0; i < times; i++) {
            int rarity;
            boolean byPity = false;
            if (legendCnt + 1 >= PITY_LEGEND) {         // 传说保底: 满 40 抽必出
                rarity = 4;
                byPity = true;
            } else if (rareCnt + 1 >= PITY_RARE) {      // 稀有保底: 满 10 抽必出稀有及以上
                rarity = Math.max(rollRarity(), 3);
                byPity = rarity == 3;
            } else {
                rarity = rollRarity();
            }
            if (rarity >= 4) legendCnt = 0; else legendCnt++;
            if (rarity >= 3) rareCnt = 0; else rareCnt++;

            Card c = pickCard(byRarity, rarity, all);
            boolean isNew = owned.add(c.getId());
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("rarity", c.getRarity());
            m.put("img", c.getImg());
            m.put("isNew", isNew);
            m.put("pity", byPity);          // 本张是否由保底产出(前端可打"保底"标签)
            if (isNew) {
                UserCard uc = new UserCard();
                uc.setUserId(userId);
                uc.setCardId(c.getId());
                userCardMapper.insert(uc);
                newlyOwned++;
            } else {
                refund += DUP_REFUND;
            }
            results.add(m);
        }
        // 保底计数落库(Redis, 90 天有效)
        savePity(userId, PITY_RARE_KEY, rareCnt);
        savePity(userId, PITY_LEGEND_KEY, legendCnt);

        // 重复卡回收固定返金币(水晶支付时也返金币)
        int balance = u.getBalance() + refund;
        if (useCrystal) {
            u.setCrystal((u.getCrystal() == null ? 0 : u.getCrystal()) - cost);
        } else {
            balance -= cost;
        }
        u.setBalance(balance);
        userMapper.updateById(u);

        // 抽卡任务进度
        bonusService.onPull(userId, times);

        GachaLog log = new GachaLog();
        log.setUserId(userId);
        log.setCost(cost);
        try {
            log.setResults(objectMapper.writeValueAsString(results));
        } catch (Exception ignored) {
            log.setResults("[]");
        }
        gachaLogMapper.insert(log);

        Map<String, Object> r = new HashMap<>();
        r.put("results", results);
        r.put("cost", cost);
        r.put("currency", useCrystal ? "crystal" : "gold");
        r.put("refund", refund);
        r.put("balance", balance);
        r.put("crystal", u.getCrystal() == null ? 0 : u.getCrystal());
        r.put("newlyOwned", newlyOwned);
        r.put("pityRare", PITY_RARE - rareCnt);      // 距离稀有保底还剩几抽
        r.put("pityLegend", PITY_LEGEND - legendCnt);
        return r;
    }

    /** 读取保底计数 */
    private int pity(Long userId, String prefix) {
        try {
            Object v = redis.opsForValue().get(prefix + userId);
            return v == null ? 0 : Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return 0;
        }
    }

    /** 写入保底计数(90 天足够长, 长期不玩也不会丢进度) */
    private void savePity(Long userId, String prefix, int n) {
        try {
            redis.opsForValue().set(prefix + userId, n, 90, java.util.concurrent.TimeUnit.DAYS);
        } catch (Exception ignored) {
        }
    }

    /** 稀有度权重: 普通60 稀有25 史诗12 传说3 */
    private int rollRarity() {
        int p = random.nextInt(100);
        if (p < 60) return 1;
        if (p < 85) return 2;
        if (p < 97) return 3;
        return 4;
    }

    /** 指定稀有度随机取卡, 该档为空则回退全池 */
    private Card pickCard(Map<Integer, List<Card>> byRarity, int rarity, List<Card> all) {
        List<Card> list = byRarity.get(rarity);
        if (list == null || list.isEmpty()) list = all;
        return list.get(random.nextInt(list.size()));
    }
}
