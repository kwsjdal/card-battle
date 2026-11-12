package com.cardbattle.service;

import com.cardbattle.battle.BattleManager;
import com.cardbattle.battle.BattlePlayer;
import com.cardbattle.common.BizException;
import com.cardbattle.entity.Card;
import com.cardbattle.entity.User;
import com.cardbattle.entity.UserDeck;
import com.cardbattle.mapper.CardMapper;
import com.cardbattle.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 匹配服务: Redis ZSet 天梯匹配队列
 *   key = match:queue, member = userId, score = 天梯分
 *   每3秒扫描一次, 相邻两名分差<=200即撮合开局
 * 演进点(论文可写): 定时轮询 -> Redis Pub/Sub -> 专用匹配网关
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private static final String QUEUE_KEY = "match:queue";
    /** 入队时间(userId -> 时间戳), 用于按等待时长放宽匹配窗口 */
    private static final String JOIN_TIME_KEY = "match:jointime";
    private static final double RATING_WINDOW = 200;
    private static final double MAX_WINDOW = 2000;
    private static final int DECK_SIZE = 30;

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserMapper userMapper;
    private final CardMapper cardMapper;
    private final DeckService deckService;
    private final BattleManager battleManager;
    private final ObjectMapper objectMapper;

    @javax.annotation.PostConstruct
    public void init() {
        redisTemplate.delete(QUEUE_KEY);      // 启动时清理残留队列(上次进程异常退出遗留)
        redisTemplate.delete(JOIN_TIME_KEY);
        log.info("MatchService 已启动, 残留匹配队列已清空");
    }

    /** 进入匹配队列(先校验卡组: 必须恰好 30 张且全部已解锁) */
    public void join(Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) return;
        // 卡组检测: 不合规就直接拒绝并给出可操作的提示, 避免开局才发现问题
        UserDeck deck = deckService.getDefault(userId);
        if (!deckService.deckValid(userId, deck)) {
            throw new BizException("卡组不完整：需要恰好 30 张且全部为已解锁卡牌，请先到「卡组编辑」组建卡组");
        }
        redisTemplate.opsForZSet().add(QUEUE_KEY, String.valueOf(userId), u.getRating());
        redisTemplate.opsForHash().put(JOIN_TIME_KEY, String.valueOf(userId), System.currentTimeMillis());
        log.info("用户[{}]进入匹配队列(天梯{})", u.getNickname(), u.getRating());
    }

    /** 离开队列 */
    public void leave(Long userId) {
        redisTemplate.opsForZSet().remove(QUEUE_KEY, String.valueOf(userId));
        redisTemplate.opsForHash().delete(JOIN_TIME_KEY, String.valueOf(userId));
    }

    /** 匹配状态(前端展示用): 队列人数 + 我已等待秒数 */
    public Map<String, Object> status(Long userId) {
        Map<String, Object> r = new HashMap<>();
        Long size = redisTemplate.opsForZSet().size(QUEUE_KEY);
        Object ts = redisTemplate.opsForHash().get(JOIN_TIME_KEY, String.valueOf(userId));
        r.put("waiting", size == null ? 0 : size);
        r.put("inQueue", ts != null);
        r.put("waited", waitedSeconds(userId));
        return r;
    }

    /** 已等待秒数 */
    private long waitedSeconds(Long userId) {
        Object ts = redisTemplate.opsForHash().get(JOIN_TIME_KEY, String.valueOf(userId));
        if (ts == null) return 0;
        try {
            return Math.max(0, (System.currentTimeMillis() - Long.parseLong(ts.toString())) / 1000);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 动态匹配窗口(论文可写): 刚入队只接受分差 <=200 的对手, 等得越久窗口越宽,
     * 等待超过 5 秒后每秒放宽 60 分, 上限 2000 分 —— 避免高分/低分玩家永远撮合不上。
     */
    private double windowFor(Long a, Long b) {
        long wait = Math.max(waitedSeconds(a), waitedSeconds(b));
        double w = RATING_WINDOW + Math.max(0, wait - 5) * 60.0;
        return Math.min(w, MAX_WINDOW);
    }

    /** 人机对战: 免匹配直接开局(真人先手 vs 训练机器人), 返回roomId */
    /**
     * 人机对战: 立即开局。
     * @param level 机器人难度 easy / normal / hard(空或非法值按 normal)
     */
    public String joinBot(Long userId, String level) {
        User u = userMapper.selectById(userId);
        if (u == null) throw new com.cardbattle.common.BizException("用户不存在");
        leave(userId);   // 防止同时在匹配队列
        BattlePlayer me = buildPlayer(userId);
        return battleManager.createBotRoom(me, level).getRoomId();
    }

    /** 定时撮合 —— 开题关键技术点③: 分段匹配 + 异常隔离保护定时线程 */
    @Scheduled(fixedDelay = 3000)
    public void tryMatch() {
        try {
            // 单次往返取回全部候选(id+分数), 避免逐人 score() 查询
            Set<ZSetOperations.TypedTuple<Object>> waiting =
                    redisTemplate.opsForZSet().rangeWithScores(QUEUE_KEY, 0, -1);
            if (waiting == null || waiting.size() < 2) return;
            log.debug("匹配队列扫描中, 当前等待玩家数={}", waiting.size());

            Long prevId = null;
            Double prevRating = null;
            for (ZSetOperations.TypedTuple<Object> t : waiting) {
                if (t.getValue() == null || t.getScore() == null) continue;
                Long uid = Long.valueOf(t.getValue().toString());
                Double rating = t.getScore();
                if (prevId != null && Math.abs(rating - prevRating) <= windowFor(prevId, uid)) {
                    log.info("撮合尝试: {}({}) vs {}({}), 窗口=±{}",
                            prevId, prevRating.intValue(), uid, rating.intValue(), (long) windowFor(prevId, uid));
                    BattlePlayer a = buildPlayer(prevId);
                    BattlePlayer b = buildPlayer(uid);
                    if (a != null && b != null) {
                        battleManager.createRoom(a, b);
                        redisTemplate.opsForZSet().remove(QUEUE_KEY, prevId.toString(), uid.toString());
                        redisTemplate.opsForHash().delete(JOIN_TIME_KEY, prevId.toString(), uid.toString());
                        log.info("撮合成功: {} vs {}", prevId, uid);
                        prevId = null;
                        prevRating = null;
                        continue;
                    } else {
                        log.warn("buildPlayer 返回 null, 跳过撮合");
                    }
                }
                prevId = uid;
                prevRating = rating;
            }
        } catch (Exception e) {
            log.error("匹配扫描异常 (定时线程已恢复, 下次3s继续)", e);
        }
    }

    private BattlePlayer buildPlayer(Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            log.warn("buildPlayer user={} not found, removed from queue", userId);
            leave(userId);
            return null;
        }
        List<Card> deck;
        Long deckId = null;                 // 卡组胜率统计用: 随机卡组兜底时为 null
        var defaultDeck = deckService.getDefault(userId);
        if (defaultDeck != null) {
            try {
                List<Long> ids = objectMapper.readValue(defaultDeck.getCardIds(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class));
                deck = loadCards(ids);
                deckId = defaultDeck.getId();
            } catch (Exception e) {
                log.warn("用户{}卡组解析失败, 使用随机卡组", userId, e);
                deck = battleManager.randomDeck();
            }
        } else {
            deck = battleManager.randomDeck();   // 没建卡组的玩家用随机卡组兜底
        }
        log.debug("buildPlayer userId={} deckSize={}", userId, deck == null ? -1 : deck.size());
        return battleManager.buildPlayer(u, deck, deckId);
    }

    /** 按卡组id列表展开为实际卡牌实例, 不足30张用随机卡补齐 */
    private List<Card> loadCards(List<Long> ids) {
        List<Long> distinct = ids.stream().distinct().collect(Collectors.toList());
        Map<Long, Card> byId = cardMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(Card::getId, c -> c));
        List<Card> deck = new ArrayList<>();
        for (Long id : ids) {
            Card c = byId.get(id);
            if (c != null) deck.add(c.copy());   // 同名卡多张必须为独立实例(hp/亡语按实例结算)
        }
        if (deck.size() < DECK_SIZE) {
            List<Card> patch = battleManager.randomDeck();
            deck.addAll(patch.subList(0, DECK_SIZE - deck.size()));
        }
        return new ArrayList<>(deck.subList(0, DECK_SIZE));
    }
}
