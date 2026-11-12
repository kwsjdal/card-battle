package com.cardbattle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cardbattle.entity.*;
import com.cardbattle.mapper.*;
import com.cardbattle.service.CardCache;
import com.cardbattle.service.StatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 个人数据中心: 把散落在各表的数据聚合成一屏可看的"游戏生涯报告"
 * 论文可写: 面向玩家的数据可视化(战绩趋势 / 收藏完成度 / 消费结构)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private static final String RANK_KEY = "rank:zset";
    private static final int TREND_DAYS = 14;

    private final UserMapper userMapper;
    private final MatchRecordMapper matchRecordMapper;
    private final RechargeLogMapper rechargeLogMapper;
    private final GachaLogMapper gachaLogMapper;
    private final UserCardMapper userCardMapper;
    private final UserDeckMapper deckMapper;
    private final UserSkinMapper userSkinMapper;
    private final UserOperatorSkinMapper userOperatorSkinMapper;
    private final CardCache cardCache;
    private final RedisTemplate<String, Object> redis;

    @Override
    public Map<String, Object> overview(Long userId) {
        User me = userMapper.selectById(userId);
        if (me == null) throw new com.cardbattle.common.BizException("用户不存在");

        Map<String, Object> r = new HashMap<>();
        r.put("user", userPart(me, userId));
        r.put("collection", collectionPart(userId));
        r.put("economy", economyPart(userId));
        r.put("recent", recentPart(userId));
        r.put("trend", trendPart(userId));
        r.put("ratingTrend", ratingTrendPart(userId));   // 天梯分走势(前端画折线)
        r.put("deckStats", deckStatsPart(userId));       // 各卡组胜率(按 deck_id 聚合)
        return r;
    }

    /**
     * 卡组胜率: 统计每套卡组出战多少局、赢多少局。
     * 只有 6.x 之后的对局才记录了 deck_id, 更早的历史数据(deck_id 为空)不计入,
     * 前端会给"暂无数据"提示, 不做假数据填充。
     */
    private List<Map<String, Object>> deckStatsPart(Long userId) {
        List<MatchRecord> recs = matchRecordMapper.selectList(new LambdaQueryWrapper<MatchRecord>()
                .and(w -> w.eq(MatchRecord::getPlayer1Id, userId).or().eq(MatchRecord::getPlayer2Id, userId))
                .orderByDesc(MatchRecord::getId));
        // deckId -> [场次, 胜, 负, 平]
        Map<Long, int[]> agg = new LinkedHashMap<>();
        for (MatchRecord rec : recs) {
            boolean isP1 = userId.equals(rec.getPlayer1Id());
            Long deckId = isP1 ? rec.getP1DeckId() : rec.getP2DeckId();
            if (deckId == null) continue;                  // 随机卡组/历史数据
            int[] a = agg.computeIfAbsent(deckId, k -> new int[4]);
            a[0]++;
            Long w = rec.getWinnerId();
            if (w == null) a[3]++;
            else if (userId.equals(w)) a[1]++;
            else a[2]++;
        }
        if (agg.isEmpty()) return new ArrayList<>();

        Map<Long, UserDeck> decks = deckMapper.selectBatchIds(agg.keySet()).stream()
                .collect(Collectors.toMap(UserDeck::getId, d -> d, (x, y) -> x));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<Long, int[]> e : agg.entrySet()) {
            int[] a = e.getValue();
            int decided = a[1] + a[2];
            Map<String, Object> m = new HashMap<>();
            m.put("deckId", e.getKey());
            UserDeck d = decks.get(e.getKey());
            m.put("name", d == null ? ("卡组#" + e.getKey()) : d.getName());
            m.put("total", a[0]);
            m.put("win", a[1]);
            m.put("lose", a[2]);
            m.put("draw", a[3]);
            m.put("winRate", decided == 0 ? 0 : Math.round(a[1] * 100f / decided));
            out.add(m);
        }
        out.sort((x, y) -> Integer.compare((Integer) y.get("total"), (Integer) x.get("total")));
        return out;
    }

    /** 账号概览: 天梯分 / 名次 / 胜负 / 胜率 */
    private Map<String, Object> userPart(User me, Long userId) {
        int win = me.getWinCount() == null ? 0 : me.getWinCount();
        int lose = me.getLoseCount() == null ? 0 : me.getLoseCount();
        int total = win + lose;
        Long rank = null;
        try {
            Long rr = redis.opsForZSet().reverseRank(RANK_KEY, userId.toString());
            if (rr != null) rank = rr + 1;
        } catch (Exception ignored) {
        }
        Map<String, Object> m = new HashMap<>();
        m.put("id", userId);
        m.put("nickname", me.getNickname());
        m.put("username", me.getUsername());
        m.put("avatar", me.getAvatar());
        m.put("rating", me.getRating());
        m.put("rank", rank);
        m.put("win", win);
        m.put("lose", lose);
        m.put("total", total);
        m.put("winRate", total == 0 ? 0 : Math.round(win * 1000.0 / total) / 10.0);
        m.put("createTime", me.getCreateTime() == null ? "" : me.getCreateTime().toString().replace('T', ' '));
        return m;
    }

    /** 收藏: 卡牌收集进度 + 稀有度分布 + 卡组数 + 皮肤数 */
    private Map<String, Object> collectionPart(Long userId) {
        List<Card> all = cardCache.all();
        Set<Long> owned = userCardMapper.selectList(new LambdaQueryWrapper<UserCard>()
                        .eq(UserCard::getUserId, userId))
                .stream().map(UserCard::getCardId).collect(Collectors.toSet());

        Map<Integer, Long> totalByRarity = new HashMap<>();
        Map<Integer, Long> ownByRarity = new HashMap<>();
        for (Card c : all) {
            int rar = c.getRarity() == null ? 1 : c.getRarity();
            totalByRarity.merge(rar, 1L, Long::sum);
            if (owned.contains(c.getId())) ownByRarity.merge(rar, 1L, Long::sum);
        }
        Map<String, Object> m = new HashMap<>();
        m.put("ownedCount", owned.size());
        m.put("totalCount", all.size());
        m.put("percent", all.isEmpty() ? 0 : Math.round(owned.size() * 1000.0 / all.size()) / 10.0);
        m.put("totalByRarity", totalByRarity);
        m.put("ownByRarity", ownByRarity);
        m.put("deckCount", deckMapper.selectCount(new LambdaQueryWrapper<UserDeck>().eq(UserDeck::getUserId, userId)));
        try {
            m.put("skinCount", userSkinMapper.selectCount(new LambdaQueryWrapper<UserSkin>().eq(UserSkin::getUserId, userId))
                    + userOperatorSkinMapper.selectCount(new LambdaQueryWrapper<UserOperatorSkin>()
                    .eq(UserOperatorSkin::getUserId, userId)));
        } catch (Exception e) {
            m.put("skinCount", 0);
        }
        return m;
    }

    /** 消费: 累计充值 / 累计抽卡花费 / 抽卡次数 */
    private Map<String, Object> economyPart(Long userId) {
        int recharge = 0;
        for (RechargeLog l : rechargeLogMapper.selectList(new LambdaQueryWrapper<RechargeLog>()
                .eq(RechargeLog::getUserId, userId))) {
            recharge += l.getAmount() == null ? 0 : l.getAmount();
        }
        int gachaCost = 0, pulls = 0;
        for (GachaLog l : gachaLogMapper.selectList(new LambdaQueryWrapper<GachaLog>()
                .eq(GachaLog::getUserId, userId))) {
            gachaCost += l.getCost() == null ? 0 : l.getCost();
            pulls += countPulls(l.getResults());
        }
        User me = userMapper.selectById(userId);
        Map<String, Object> m = new HashMap<>();
        m.put("rechargeTotal", recharge);
        m.put("gachaCost", gachaCost);
        m.put("pulls", pulls);
        m.put("balance", me == null ? 0 : me.getBalance());
        m.put("crystal", me == null || me.getCrystal() == null ? 0 : me.getCrystal());
        return m;
    }

    /** 最近 10 场对局(对手昵称 / 胜负 / 回合数) */
    private List<Map<String, Object>> recentPart(Long userId) {
        List<MatchRecord> recs = matchRecordMapper.selectList(new LambdaQueryWrapper<MatchRecord>()
                .and(w -> w.eq(MatchRecord::getPlayer1Id, userId).or().eq(MatchRecord::getPlayer2Id, userId))
                .orderByDesc(MatchRecord::getCreateTime)
                .last("LIMIT 10"));
        Set<Long> foeIds = new HashSet<>();
        for (MatchRecord r : recs) {
            Long foe = userId.equals(r.getPlayer1Id()) ? r.getPlayer2Id() : r.getPlayer1Id();
            if (foe != null && foe > 0) foeIds.add(foe);
        }
        Map<Long, String> names = new HashMap<>();
        if (!foeIds.isEmpty()) {
            for (User u : userMapper.selectBatchIds(foeIds)) names.put(u.getId(), u.getNickname());
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (MatchRecord r : recs) {
            Long foe = userId.equals(r.getPlayer1Id()) ? r.getPlayer2Id() : r.getPlayer1Id();
            String result = r.getWinnerId() == null ? "draw" : (r.getWinnerId().equals(userId) ? "win" : "lose");
            Map<String, Object> m = new HashMap<>();
            m.put("roomId", r.getRoomId());
            m.put("opponent", foe != null && foe < 0 ? "训练机器人" : names.getOrDefault(foe, "未知玩家"));
            m.put("result", result);
            m.put("rounds", r.getRounds());
            m.put("time", r.getCreateTime() == null ? "" : r.getCreateTime().toString().replace('T', ' ').substring(0, 16));
            list.add(m);
        }
        return list;
    }

    /** 近 14 天每日对局数与胜场 */
    private List<Map<String, Object>> trendPart(Long userId) {
        LocalDateTime from = LocalDate.now().minusDays(TREND_DAYS - 1).atStartOfDay();
        List<MatchRecord> recs = matchRecordMapper.selectList(new LambdaQueryWrapper<MatchRecord>()
                .and(w -> w.eq(MatchRecord::getPlayer1Id, userId).or().eq(MatchRecord::getPlayer2Id, userId))
                .ge(MatchRecord::getCreateTime, from));

        Map<String, int[]> byDay = new LinkedHashMap<>();   // date -> {games, wins}
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            byDay.put(LocalDate.now().minusDays(i).format(fmt), new int[2]);
        }
        for (MatchRecord r : recs) {
            if (r.getCreateTime() == null) continue;
            String d = r.getCreateTime().toLocalDate().format(fmt);
            int[] v = byDay.get(d);
            if (v == null) continue;
            v[0]++;
            if (r.getWinnerId() != null && r.getWinnerId().equals(userId)) v[1]++;
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, int[]> e : byDay.entrySet()) {
            Map<String, Object> m = new HashMap<>();
            m.put("date", e.getKey());
            m.put("games", e.getValue()[0]);
            m.put("wins", e.getValue()[1]);
            list.add(m);
        }
        return list;
    }

    /**
     * 天梯分走势: 按对局时间正序输出每局结束后的积分。
     *
     * 记分表里只存了胜负、没存"打完这局是多少分", 所以用**倒推**还原:
     * 最后一局结束后的分数就是当前天梯分(准确值), 往前逐局减去该局的增量即可。
     * 这样即使中途被管理员改过分, 终点仍与真实值一致(起点则是推算值)。
     */
    private List<Map<String, Object>> ratingTrendPart(Long userId) {
        User me = userMapper.selectById(userId);
        int cur = (me == null || me.getRating() == null) ? 1000 : me.getRating();

        List<MatchRecord> recs = matchRecordMapper.selectList(new LambdaQueryWrapper<MatchRecord>()
                .and(w -> w.eq(MatchRecord::getPlayer1Id, userId).or().eq(MatchRecord::getPlayer2Id, userId))
                .orderByAsc(MatchRecord::getCreateTime));

        int n = recs.size();
        List<Integer> deltas = new ArrayList<>(n);
        for (MatchRecord r : recs) {
            Long w = r.getWinnerId();
            // 胜 +25 / 负 -25 / 平局 0(与 BattleManager 结算口径一致)
            deltas.add(w == null ? 0 : (w.equals(userId) ? 25 : -25));
        }

        List<Map<String, Object>> list = new ArrayList<>(n);
        int score = cur;                       // 从"当前分"往回推
        for (int i = n - 1; i >= 0; i--) {
            MatchRecord r = recs.get(i);
            int d = deltas.get(i);
            Map<String, Object> m = new HashMap<>();
            m.put("rating", score);
            m.put("delta", d);
            m.put("result", d > 0 ? "win" : (d < 0 ? "lose" : "draw"));
            m.put("rounds", r.getRounds());
            m.put("roomId", r.getRoomId());
            m.put("time", r.getCreateTime() == null ? ""
                    : r.getCreateTime().toString().replace('T', ' ').substring(0, 16));
            list.add(0, m);
            score -= d;
        }
        return list;
    }

    /** 从抽卡结果 JSON 里数出实际抽了几张(结果数组每项都含 isNew) */
    private int countPulls(String json) {
        if (json == null || json.isEmpty()) return 0;
        int n = 0, idx = 0;
        while ((idx = json.indexOf("isNew", idx)) >= 0) { n++; idx += 5; }
        return n;
    }
}
