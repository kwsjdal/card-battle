package com.cardbattle.battle;

import com.cardbattle.common.BizException;
import com.cardbattle.entity.Card;
import com.cardbattle.entity.MatchRecord;
import com.cardbattle.entity.User;
import com.cardbattle.mapper.MatchRecordMapper;
import com.cardbattle.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 战斗房间管理器: 内存态单例
 * 演进点(论文可写): 单机内存 -> Redis集群会话 -> 分布式房间路由
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BattleManager {

    public static final long BOT_ID = -1L;
    public static final String BOT_NICK = "训练机器人·佩佩";

    private final Map<String, BattleRoom> rooms = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate template;   // WebSocket 推送
    private final RedisTemplate<String, Object> redis; // Redis 幂等 + 天梯
    private final com.cardbattle.service.CardCache cardCache;
    private final MatchRecordMapper matchRecordMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final com.cardbattle.service.BonusService bonusService;
    private final SecureRandom random = new SecureRandom();
    private final ScheduledExecutorService botScheduler = Executors.newSingleThreadScheduledExecutor();
    /** 房间保洁线程: 定时扫描并回收超时房间 */
    private final ScheduledExecutorService janitor = Executors.newSingleThreadScheduledExecutor();

    /** 房间保洁阈值, 可用 application.yml 覆盖 */
    @org.springframework.beans.factory.annotation.Value("${cardbattle.battle.idle-timeout-ms:300000}")
    private long cfgIdleTimeout = 300000;
    @org.springframework.beans.factory.annotation.Value("${cardbattle.battle.max-alive-ms:2700000}")
    private long cfgMaxAlive = 2700000;
    @org.springframework.beans.factory.annotation.Value("${cardbattle.battle.offline-grace-ms:60000}")
    private long cfgOfflineGrace = 60000;

    /** 启动保洁任务: 加载阈值 + 每 30 秒扫描一次全部房间 */
    @PostConstruct
    public void startJanitor() {
        BattleRoom.IDLE_TIMEOUT_MS = Math.max(30_000, cfgIdleTimeout);
        BattleRoom.MAX_ALIVE_MS = Math.max(BattleRoom.IDLE_TIMEOUT_MS, cfgMaxAlive);
        BattleRoom.OFFLINE_GRACE_MS = Math.max(5_000, cfgOfflineGrace);
        janitor.scheduleAtFixedRate(this::sweep, 30, 30, TimeUnit.SECONDS);
        log.info("房间保洁任务已启动: 空闲>{}s 判和回收, 掉线>{}s 判负, 单局上限>{}min",
                BattleRoom.IDLE_TIMEOUT_MS / 1000, BattleRoom.OFFLINE_GRACE_MS / 1000, BattleRoom.MAX_ALIVE_MS / 60000);
    }

    /**
     * 房间超时清理(论文可写: 内存态资源回收 / 防挂机占坑)
     * 1) 掉线超过宽限期 -> 掉线方判负
     * 2) 单局总时长超上限 -> 判和
     * 3) 空闲(无任何出牌/攻击/结束回合操作)超时 -> 判和
     */
    public void sweep() {
        try {
            for (BattleRoom room : new ArrayList<>(rooms.values())) {
                if (room.getPhase() == BattleRoom.Phase.GAME_OVER) {   // 已结束却没销毁的残留房间
                    rooms.remove(room.getRoomId());
                    continue;
                }
                String reason = null;
                Long loser = null;
                if (room.maxOfflineMillis() > BattleRoom.OFFLINE_GRACE_MS) {
                    reason = "OFFLINE";
                    loser = room.offlineUserIds().isEmpty() ? null : room.offlineUserIds().get(0);
                } else if (room.aliveMillis() > BattleRoom.MAX_ALIVE_MS) {
                    reason = "LIMIT";
                } else if (room.idleMillis() > BattleRoom.IDLE_TIMEOUT_MS) {
                    reason = "IDLE";
                }
                if (reason == null) continue;

                if (room.forceEnd(reason, loser) == null) { rooms.remove(room.getRoomId()); continue; }
                for (BattlePlayer p : room.getPlayers()) {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "room_closed");
                    msg.put("reason", reason);
                    msg.put("winnerId", room.getWinnerId());
                    msg.put("snapshot", room.snapshotFor(p.getUserId()));
                    sendToUser(p.getUserId(), msg);
                }
                log.info("房间{} 超时回收({}): 空闲{}s/存活{}s, 结果={}", room.getRoomId(), reason,
                        room.idleMillis() / 1000, room.aliveMillis() / 1000,
                        room.getWinnerId() == null ? "和局" : "胜者" + room.getWinnerId());
                finish(room, room.getWinnerId());
            }
        } catch (Exception e) {
            log.warn("房间保洁异常: {}", e.getMessage());
        }
    }

    /** 当前存活房间数(管理员面板可展示) */
    public int roomCount() { return rooms.size(); }

    /** WebSocket 会话计数: 同一账号可开多个页面, 计数用于判断"真掉线" */
    private final Map<Long, Integer> wsSessions = new ConcurrentHashMap<>();

    public void wsConnect(Long userId) {
        if (userId == null || userId < 0) return;
        wsSessions.merge(userId, 1, Integer::sum);
    }

    /** @return true=该账号最后一个会话断开(真掉线) */
    public boolean wsDisconnect(Long userId) {
        if (userId == null || userId < 0) return false;
        return wsSessions.computeIfPresent(userId, (k, v) -> v <= 1 ? null : v - 1) == null;
    }

    /** 创建人机房间: 真人先手 vs 训练机器人 */
    public BattleRoom createBotRoom(User u, List<Card> deck, String level) {
        return createBotRoom(buildPlayer(u, deck), level);
    }

    /** 创建人机房间(玩家对象由调用方构造, 便于带上 deckId 做卡组胜率统计) */
    public BattleRoom createBotRoom(BattlePlayer me, String level) {
        BattlePlayer bot = buildBotPlayer();
        BattleRoom room = createRoom(me, bot);
        room.setBotLevel(normalizeLevel(level));
        return room;
    }

    /** 难度归一化: 只认 easy / normal / hard, 其余一律按 normal, 避免脏数据影响 AI 分支判断 */
    private String normalizeLevel(String level) {
        if (level == null) return "normal";
        String s = level.trim().toLowerCase(Locale.ROOT);
        return (s.equals("easy") || s.equals("hard")) ? s : "normal";
    }

    /** 构造机器人玩家(负数userId, 不参与天梯与注册体系) */
    public BattlePlayer buildBotPlayer() {
        BattlePlayer bot = new BattlePlayer();
        bot.setUserId(BOT_ID);
        bot.setNickname(BOT_NICK);
        bot.setDeck(randomDeck());
        return bot;
    }

    /** 若当前行动方是机器人, 延时1.2秒调度AI行动(模拟思考时间) */
    public void maybeBotTurn(BattleRoom room) {
        if (room.getPhase() != BattleRoom.Phase.MAIN) return;
        BattlePlayer cur = room.getPlayers()[room.getCurrent()];
        if (cur.getUserId() >= 0) return;
        botScheduler.schedule(() -> botAct(room), 1200, TimeUnit.MILLISECONDS);
    }

    /**
     * AI 决策器: 贪心出牌 -> 嘲讽约束下攻击 -> 补刀/打脸 -> 结束回合
     *
     * 【重要】任何一步异常都不能让回合停在机器人手里 —— 早期版本一旦攻击被"嘲讽"拒绝,
     *         异常会直接跳出本方法, endTurn 永远不执行, 对局就此卡死到 45 分钟上限。
     *         现在所有操作单点容错, 并由 endBotTurn 保证回合一定流转出去。
     */
    private void botAct(BattleRoom room) {
        if (room.getPhase() != BattleRoom.Phase.MAIN) return;
        Long botId = room.getPlayers()[room.getCurrent()].getUserId();
        try {
            BattlePlayer me = room.getPlayers()[room.getCurrent()];
            BattlePlayer foe = room.getPlayers()[1 - room.getCurrent()];

            // 1. 出牌阶段: 反复扫描手牌, 费用付得起就打出(贪心铺场)
            //    简单难度刻意少铺, 给新手留出喘息空间
            String lv = room.getBotLevel();
            int playCap = "easy".equals(lv) ? 2 : 99;
            int playedCount = 0;
            boolean played = true;
            while (played && playedCount < playCap && room.getPhase() == BattleRoom.Phase.MAIN) {
                played = false;
                for (int i = 0; i < me.getHand().size(); i++) {
                    Card c = me.getHand().get(i);
                    if (c.getCost() <= me.getMana() && me.getBoard().size() < 7) {
                        room.playCard(botId, i);
                        played = true;
                        playedCount++;
                        break;
                    }
                }
            }
            // 2. 攻击阶段: 依次用随从攻击。目标由 pickAttackTarget 统一裁决(遵守嘲讽规则),
            //    单次攻击失败只跳过该随从, 不再中断整个回合。
            while (room.getPhase() == BattleRoom.Phase.MAIN) {
                int src = -1;
                for (int i = 0; i < me.getBoard().size(); i++) {
                    Card m = me.getBoard().get(i);
                    // 跳过已攻击过 / 召唤失调(上回合之后才能动)的随从
                    if (!me.getActed().contains(m) && !me.getSleeping().contains(m)) { src = i; break; }
                }
                if (src < 0) break;   // 场上随从全部已行动
                Card atk = me.getBoard().get(src);
                // 简单难度: 只让一半随从动手, 且不主动抢血
                if ("easy".equals(lv) && !me.getActed().isEmpty()
                        && foe.getHp() > 10) { me.getActed().add(atk); continue; }
                try {
                    int dst = pickAttackTarget(room, foe, atk, lv);
                    // 突袭随从登场当回合不能打脸: 裁决结果是英雄且敌方有随从时改打随从,
                    // 敌方场上空着就放弃这次攻击(标记已行动, 避免死循环)
                    if (dst == -1 && me.getRushLock().contains(atk)) {
                        if (foe.getBoard().isEmpty()) { me.getActed().add(atk); continue; }
                        dst = 0;
                    }
                    room.attack(botId, src, dst);
                } catch (BizException e) {
                    log.debug("机器人攻击被拒(房间{}): {}", room.getRoomId(), e.getMessage());
                    me.getActed().add(atk);   // 标记已行动, 防止同一随从被反复重试形成死循环
                }
            }
            // 2.5 英雄技能(2费1点伤害): 优先补掉敌方 1 血随从, 没有就打脸
            //     简单难度不使用技能, 降低压迫感
            if (room.getPhase() == BattleRoom.Phase.MAIN
                    && !"easy".equals(lv)
                    && me.getMana() >= BattleRoom.HERO_POWER_COST && !me.isHeroPowerUsed()) {
                int kill = -1;
                for (int i = 0; i < foe.getBoard().size(); i++) {
                    if (foe.getBoard().get(i).getHp() <= BattleRoom.HERO_POWER_DAMAGE) { kill = i; break; }
                }
                try { room.heroPower(botId, kill); } catch (BizException e) {
                    log.debug("机器人技能未命中(房间{}): {}", room.getRoomId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("机器人行动异常(房间{}): {}", room.getRoomId(), e.getMessage());
        } finally {
            endBotTurn(room, botId);
        }
    }

    /**
     * 机器人攻击目标裁决:
     *   - 敌方场上有嘲讽随从 -> 只能打嘲讽(优先能斩杀的那个)
     *   - 否则优先斩杀残血随从, 没有可斩杀的就直击英雄(-1)
     *   - hard 额外做一件事: 同样能斩杀时优先解决攻击力更高的威胁(换牌更划算)
     */
    private int pickAttackTarget(BattleRoom room, BattlePlayer foe, Card atk, String lv) {
        List<Integer> taunts = room.tauntIdx(foe);
        if (!taunts.isEmpty()) {
            for (int i : taunts) {                       // 嘲讽里挑一个能一击斩杀的
                if (foe.getBoard().get(i).getHp() <= atk.getAttack()) return i;
            }
            return taunts.get(0);                        // 斩不掉就打第一个嘲讽
        }
        int best = -1;
        int bestThreat = Integer.MIN_VALUE;
        for (int i = 0; i < foe.getBoard().size(); i++) {
            Card c = foe.getBoard().get(i);
            if (c.getHp() > atk.getAttack()) continue;   // 斩不掉的跳过
            if (!"hard".equals(lv)) return i;            // 普通难度: 能斩就斩, 取第一个
            // 困难难度: 在能斩杀的目标里挑威胁最大的(攻击力 × 2 + 血量)
            int threat = c.getAttack() * 2 + c.getHp();
            if (threat > bestThreat) { bestThreat = threat; best = i; }
        }
        if (best >= 0) return best;
        return -1;                                       // 无可斩杀目标 -> 打脸
    }

    /** 结束机器人回合并广播。防御式: 无论前面出过什么错, 回合都必须流转出去, 否则双端一起卡死 */
    private void endBotTurn(BattleRoom room, Long botId) {
        try {
            if (room.getPhase() == BattleRoom.Phase.GAME_OVER) {
                sendGameOver(room);
                finish(room, room.getWinnerId());
                return;
            }
            if (room.getPhase() == BattleRoom.Phase.MAIN
                    && room.getPlayers()[room.getCurrent()].getUserId().equals(botId)) {
                room.endTurn(botId);
            }
            broadcast(room);
        } catch (Exception e) {
            log.warn("机器人结束回合失败(房间{}): {}", room.getRoomId(), e.getMessage());
        }
    }

    /** 创建房间并开局, 双方收到 match_found + battle_start */
    public BattleRoom createRoom(BattlePlayer a, BattlePlayer b) {
        String roomId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        BattleRoom room = new BattleRoom(roomId, a, b);
        rooms.put(roomId, room);
        Map<String, Object> found = new HashMap<>();
        found.put("event", "match_found");
        found.put("roomId", roomId);
        found.put("opponents", List.of(a.getNickname(), b.getNickname()));
        sendToUser(a.getUserId(), found);
        sendToUser(b.getUserId(), found);

        room.start();
        // 开局快照按人定制(手牌私密化)
        sendToUser(a.getUserId(), room.snapshotFor(a.getUserId()));
        sendToUser(b.getUserId(), room.snapshotFor(b.getUserId()));
        maybeBotTurn(room);   // 保险: 若机器人先手则触发AI
        log.info("房间{}开局: {} vs {}", roomId, a.getNickname(), b.getNickname());
        return room;
    }

    public BattleRoom get(String roomId) {
        return rooms.get(roomId);
    }

    /** 房间实时状态: 按人推送私密快照 + 观战频道推送脱敏快照 */
    public void broadcast(BattleRoom room) {
        for (BattlePlayer p : room.getPlayers()) {
            sendToUser(p.getUserId(), room.snapshotFor(p.getUserId()));
        }
        sendSpectators(room);
    }

    /** 观战频道: 只推脱敏快照(无一手牌), 观众订阅 /topic/battle/{roomId} */
    private void sendSpectators(BattleRoom room) {
        if (room.getPhase() == BattleRoom.Phase.GAME_OVER) return;
        try {
            template.convertAndSend("/topic/battle/" + room.getRoomId(), room.spectatorView());
        } catch (Exception e) {
            log.debug("观战推送失败(无观众也算正常): {}", e.getMessage());
        }
    }

    /** 正在进行中的对局列表(观战大厅用) */
    public List<Map<String, Object>> liveRooms() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (BattleRoom r : rooms.values()) {
            if (r.getPhase() == BattleRoom.Phase.GAME_OVER) continue;
            Map<String, Object> m = new HashMap<>();
            m.put("roomId", r.getRoomId());
            m.put("turnNo", r.getTurnNo());
            m.put("p1", r.getPlayers()[0].getNickname());
            m.put("p2", r.getPlayers()[1].getNickname());
            m.put("hp1", r.getPlayers()[0].getHp());
            m.put("hp2", r.getPlayers()[1].getHp());
            m.put("idleSec", (int) (r.idleMillis() / 1000));
            list.add(m);
        }
        return list;
    }

    /** 推送终局事件(按人定制快照) */
    public void sendGameOver(BattleRoom room) {
        for (BattlePlayer p : room.getPlayers()) {
            Map<String, Object> over = new HashMap<>();
            over.put("event", "game_over");
            over.put("winnerId", room.getWinnerId());
            over.put("snapshot", room.snapshotFor(p.getUserId()));
            sendToUser(p.getUserId(), over);
        }
    }

    /** 按玩家查存活房间(断线判负用) */
    public BattleRoom findByUser(Long userId) {
        return rooms.values().stream()
                .filter(r -> r.getPhase() != BattleRoom.Phase.GAME_OVER)
                .filter(r -> Arrays.stream(r.getPlayers()).anyMatch(p -> p.getUserId().equals(userId)))
                .findFirst().orElse(null);
    }

    /** 结算: 记录入库 + 天梯分更新(+25/-25) + 房间销毁
     * 开题关键技术点④: 幂等key (roomId+phase) 防止重复结算
     * 开题关键技术点③: 同步 Redis ZSet 天梯
     */
    public void finish(BattleRoom room, Long winnerId) {
        String idemKey = "battle:settled:" + room.getRoomId();
        // 幂等保护: roomId 只结算一次, 防止断线重连/网络抖动导致重复 finish
        Boolean first = redis.opsForValue().setIfAbsent(idemKey, winnerId == null ? "draw" : winnerId.toString(),
                24, java.util.concurrent.TimeUnit.HOURS);
        if (first == null || !first) {
            log.warn("对局 {} 已结算过, 忽略本次重复调用", room.getRoomId());
            return;
        }
        try {
            MatchRecord rec = new MatchRecord();
            rec.setRoomId(room.getRoomId());
            rec.setPlayer1Id(room.getPlayers()[0].getUserId());
            rec.setP1DeckId(room.getPlayers()[0].getDeckId());
            rec.setPlayer2Id(room.getPlayers()[1].getUserId());
            rec.setP2DeckId(room.getPlayers()[1].getDeckId());
            rec.setWinnerId(winnerId);
            rec.setRounds(room.getTurnNo());
            rec.setDetail(objectMapper.writeValueAsString(room.getLog()));
            matchRecordMapper.insert(rec);

            if (winnerId != null) {          // 平局双方不加不减
                Long loserId = winnerId.equals(rec.getPlayer1Id()) ? rec.getPlayer2Id() : rec.getPlayer1Id();
                int winNewRating = changeRating(winnerId, +25, 1);
                int loseNewRating = changeRating(loserId, -25, 0);
                // 同步 Redis ZSet (机器人等非注册用户不进天梯)
                if (winNewRating >= 0) redis.opsForZSet().add("rank:zset", winnerId.toString(), winNewRating);
                if (loseNewRating >= 0) redis.opsForZSet().add("rank:zset", loserId.toString(), loseNewRating);
                log.info("结算完成: winner={}(→{}分)  loser={}(→{}分)", winnerId, winNewRating, loserId, loseNewRating);
            } else {
                log.info("对局{}平局, 天梯分不变", room.getRoomId());
            }
            // 每日任务进度: 双方 +对局, 胜方 +胜利
            bonusService.onBattleEnd(rec.getPlayer1Id(), rec.getPlayer2Id(), winnerId);
        } catch (Exception e) {
            log.error("结算入库失败", e);
        }
        rooms.remove(room.getRoomId());
        log.info("房间{}已结算销毁", room.getRoomId());
    }

    /** 更新 DB + 返回新 rating (用户不存在返回-1, 如机器人) */
    private int changeRating(Long userId, int delta, int winFlag) {
        User u = userMapper.selectById(userId);
        if (u == null) return -1;
        int newRating = Math.max(0, u.getRating() + delta);
        u.setRating(newRating);
        if (winFlag == 1) u.setWinCount(u.getWinCount() + 1);
        else u.setLoseCount(u.getLoseCount() + 1);
        userMapper.updateById(u);
        return newRating;
    }

    /** 生成随机卡组(玩家还没有卡组时兜底), 卡池走本地缓存 */
    public List<Card> randomDeck() {
        List<Card> all = cardCache.all();
        List<Card> deck = new ArrayList<>();
        Map<Long, Integer> cnt = new HashMap<>();
        int maxPerCard = Math.max(2, (int) Math.ceil(30.0 / Math.max(all.size(), 1))); // 与 DeckService 同名卡上限规则一致, 12种卡->3张防死循环
        while (deck.size() < 30) {
            Card c = all.get(random.nextInt(all.size()));
            if (cnt.merge(c.getId(), 1, Integer::sum) <= maxPerCard) deck.add(c.copy());   // 独立实例: 同名卡hp/亡语按实例结算
        }
        return deck;
    }

    public BattlePlayer buildPlayer(User u, List<Card> deck) {
        return buildPlayer(u, deck, null);
    }

    /** @param deckId 本局使用的卡组id(随机卡组兜底时为 null), 用于结算后统计各卡组胜率 */
    public BattlePlayer buildPlayer(User u, List<Card> deck, Long deckId) {
        BattlePlayer p = new BattlePlayer();
        p.setUserId(u.getId());
        p.setNickname(u.getNickname());
        p.setDeck(new ArrayList<>(deck));
        p.setDeckId(deckId);
        return p;
    }

    private void sendToUser(Long userId, Object msg) {
        template.convertAndSend("/topic/user/" + userId, msg);
    }

    /**
     * 操作失败回执。
     * Spring 默认不会把 @MessageMapping 抛出的异常发回客户端(只写服务端日志),
     * 结果就是玩家点了法力不够的牌、或被嘲讽拦下, 界面却毫无反应。
     * 这里把异常统一推回操作者本人的 /topic/user/{uid} 频道, 由前端提示。
     */
    public void sendError(Long userId, String msg) {
        if (userId == null || msg == null) return;
        Map<String, Object> m = new HashMap<>();
        m.put("event", "error");
        m.put("msg", msg);
        sendToUser(userId, m);
    }

    @PreDestroy
    public void shutdown() {
        botScheduler.shutdownNow();
        janitor.shutdownNow();
        log.info("服务关闭, 当前存活房间数: {}", rooms.size());
    }
}
