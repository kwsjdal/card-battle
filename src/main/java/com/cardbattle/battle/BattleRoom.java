package com.cardbattle.battle;

import com.cardbattle.common.BizException;
import com.cardbattle.entity.Card;
import lombok.Getter;
import lombok.Setter;

import java.security.SecureRandom;
import java.util.*;

/**
 * 战斗房间 = 回合制状态机核心
 * 状态流转: match_found -> battle_start -> (DRAW阶段自动 -> MAIN阶段操作) 循环 -> game_over
 * 所有操作方法synchronized, 保证同一房间操作串行化(论文可展开: 分布式锁演进)
 */
@Getter
public class BattleRoom {

    public enum Phase { MATCH_FOUND, BATTLE_START, MAIN, GAME_OVER }

    private final String roomId;
    private final BattlePlayer[] players = new BattlePlayer[2];
    private int current;          // 当前行动方下标 0/1
    private int turnNo = 1;
    private static final int MAX_TURNS = 120;   // 回合上限, 超限判平局

    private Phase phase = Phase.MATCH_FOUND;
    private Long winnerId;

    /** 机器人难度: easy / normal / hard —— 只影响人机对战里 AI 的决策强度 */
    @Setter
    private String botLevel = "normal";
    private final SecureRandom random = new SecureRandom();
    private final List<Map<String, Object>> log = new ArrayList<>();   // 战斗日志, 结算入库预留
    private long logSeq = 0;                                           // 日志自增序号(前端拼历史用)
    private long actionSeq = 0;                                        // 动作序号, 前端据此播放攻击动画
    private Map<String, Object> lastAction;                            // 最近一次攻击动作快照

    /** 房间创建时间 / 最后一次有效操作时间 —— 供"房间空闲超时回收"与"单局总时长上限"判定 */
    private final long createdAt = System.currentTimeMillis();
    private volatile long lastActiveAt = System.currentTimeMillis();

    /** 英雄技能: 2 费对任意目标造成 1 点伤害, 每回合限一次 */
    public static final int HERO_POWER_COST = 2;
    public static final int HERO_POWER_DAMAGE = 1;

    /**
     * 房间保洁阈值(可由 application.yml 的 cardbattle.battle.* 覆盖, 启动时由 BattleManager 注入)
     * - 空闲超时: 无任何出牌/攻击/结束回合操作 -> 判和局并回收房间, 防挂机占内存
     * - 总时长上限: 单局最长 45 分钟
     * - 掉线宽限期: 60 秒内重连不判负(手机切后台/弱网抖动容错)
     */
    public static volatile long IDLE_TIMEOUT_MS = 5 * 60 * 1000L;
    public static volatile long MAX_ALIVE_MS = 45 * 60 * 1000L;
    public static volatile long OFFLINE_GRACE_MS = 60 * 1000L;

    public BattleRoom(String roomId, BattlePlayer a, BattlePlayer b) {
        this.roomId = roomId;
        players[0] = a;
        players[1] = b;
    }

    /** 刷新活跃时间(任何有效操作都调用) */
    private void touch() { lastActiveAt = System.currentTimeMillis(); }

    /** 距离最后一次操作的空闲毫秒数 */
    public long idleMillis() { return System.currentTimeMillis() - lastActiveAt; }

    /** 房间自创建起存活的毫秒数 */
    public long aliveMillis() { return System.currentTimeMillis() - createdAt; }

    /** 标记某玩家掉线(宽限期内重连则清除) */
    public void markOffline(Long userId) {
        for (BattlePlayer p : players) if (p.getUserId().equals(userId)) p.setOfflineSince(System.currentTimeMillis());
    }

    /** 标记某玩家重新上线 */
    public void markOnline(Long userId) {
        for (BattlePlayer p : players) if (p.getUserId().equals(userId)) p.setOfflineSince(null);
    }

    /** 处于掉线状态的玩家 id 列表 */
    public List<Long> offlineUserIds() {
        List<Long> r = new ArrayList<>();
        for (BattlePlayer p : players) if (p.getOfflineSince() != null) r.add(p.getUserId());
        return r;
    }

    /** 掉线持续时间最长的毫秒数; 无人掉线返回 -1（机器人不纳入判定） */
    public long maxOfflineMillis() {
        long max = -1;
        long now = System.currentTimeMillis();
        for (BattlePlayer p : players) {
            if (p.getUserId() < 0) continue;                 // 机器人不判掉线
            if (p.getOfflineSince() != null) max = Math.max(max, now - p.getOfflineSince());
        }
        return max;
    }

    /**
     * 强制结束(房间超时清理用)
     * @param reason IDLE=空闲超时(判和局, 不动天梯) / OFFLINE=掉线超时(掉线方判负) / LIMIT=超出单局总时长(判和局)
     * @param loserId 判负方; 为 null 时判和局
     */
    public synchronized Map<String, Object> forceEnd(String reason, Long loserId) {
        if (phase == Phase.GAME_OVER) return null;
        if ("IDLE".equals(reason)) log("system", "双方长时间无操作, 房间已回收, 本局判和");
        else if ("LIMIT".equals(reason)) log("system", "本局达到时长上限, 房间已回收, 本局判和");
        else log("system", "对手掉线超时未重连, 判定其负局");
        if (loserId != null) {
            winnerId = players[0].getUserId().equals(loserId) ? players[1].getUserId() : players[0].getUserId();
        } else {
            winnerId = null;
        }
        return gameOver();
    }

    /** 开局: 洗牌, 先手4张后手5张, 后手补偿1点临时法力(简化: 不做硬币) */
    public synchronized Map<String, Object> start() {
        for (BattlePlayer p : players) {
            Collections.shuffle(p.getDeck(), random);
            for (int i = 0; i < (players[1] == p ? 5 : 4); i++) {
                Card c = p.drawOne();
                if (c == null) break;          // 牌库不足(正常卡组30张不会触发), 防止把 null 塞进手牌
                p.getHand().add(c);
            }
        }
        phase = Phase.BATTLE_START;
        log("system", "战斗开始! " + players[0].getNickname() + " 先手");
        beginTurn();
        return snapshot();
    }

    /** 出牌: 手牌 -> 场上 */
    public synchronized void playCard(Long userId, int handIndex) {
        assertMyTurn(userId);
        touch();
        BattlePlayer p = players[current];
        if (handIndex < 0 || handIndex >= p.getHand().size()) throw new BizException("非法手牌下标");
        Card c = p.getHand().get(handIndex);
        if (c.getCost() > p.getMana()) throw new BizException("法力值不足");
        if (p.getBoard().size() >= 7) throw new BizException("场上已满(最多7个随从)");

        p.getHand().remove(handIndex);
        p.setMana(p.getMana() - c.getCost());
        p.getBoard().add(c);
        // 召唤失调: 刚上场的随从本回合不可攻击, 拥有「冲锋」或「突袭」的除外
        boolean rush = hasKeyword(c, "rush");
        if (!hasKeyword(c, "charge") && !rush) p.getSleeping().add(c);
        // 突袭: 登场当回合可以攻击随从, 但不能攻击英雄 —— 记入 rushLock, 回合结束时清空
        if (rush) p.getRushLock().add(c);
        log(userId.toString(), "打出了 " + c.getName()
                + (hasKeyword(c, "charge") ? " (冲锋: 立即可攻击)"
                : rush ? " (突袭: 本回合可攻击随从)" : ""));
        resolveAbilities(p, players[1 - current], c, "battlecry");   // 战吼
    }

    /** 攻击: 我方board[src] -> 对方board[dst] 或对方英雄(dst=-1) */
    public synchronized void attack(Long userId, int src, int dst) {
        assertMyTurn(userId);
        touch();
        BattlePlayer me = players[current];
        BattlePlayer foe = players[1 - current];
        if (src < 0 || src >= me.getBoard().size()) throw new BizException("非法攻击方下标");

        Card atk = me.getBoard().get(src);
        if (me.getSleeping().contains(atk))
            throw new BizException("召唤失调：「" + atk.getName() + "」下个回合才能攻击（拥有「冲锋」的随从上场即可攻击）");
        if (me.getActed().contains(atk)) throw new BizException("该随从本回合已攻击过");
        // 突袭: 登场当回合只能打随从, 不能直接打脸
        if (dst == -1 && me.getRushLock().contains(atk))
            throw new BizException("「突袭」随从登场当回合只能攻击随从, 不能攻击英雄");
        // 嘲讽规则: 敌方场上有嘲讽随从时, 只能攻击嘲讽随从(不能直击英雄/其他随从)
        if (dst != -1 && dst < foe.getBoard().size()) {
            Card target = foe.getBoard().get(dst);
            if (!hasKeyword(target, "taunt") && foe.getBoard().stream().anyMatch(m -> hasKeyword(m, "taunt")))
                throw new BizException("敌方存在「嘲讽」随从, 必须先攻击它");
        } else if (dst == -1 && foe.getBoard().stream().anyMatch(m -> hasKeyword(m, "taunt"))) {
            throw new BizException("敌方存在「嘲讽」随从, 无法直接攻击英雄");
        }

        // 攻击动作事件: 供前端播放突进/受击/伤害数字动画
        Map<String, Object> ev = new HashMap<>();
        ev.put("seq", ++actionSeq);
        ev.put("type", "attack");
        ev.put("attackerUserId", userId);
        ev.put("src", src);
        ev.put("dst", dst);
        ev.put("damage", atk.getAttack());
        ev.put("attackerName", atk.getName());
        boolean attackerDied = false, targetDied = false;
        int healed = 0;                 // 本回合吸血回血总量(前端播绿色治疗飘字)
        boolean shieldBroken = false;   // 是否击碎了圣盾(前端播破盾闪光)
        boolean poisonKill = false;     // 剧毒是否直接摧毁了随从(前端播剧毒特效)

        if (dst == -1) {
            ev.put("targetName", "英雄");
            foe.setHp(foe.getHp() - atk.getAttack());
            log(userId.toString(), atk.getName() + " 直接攻击对方英雄, 造成" + atk.getAttack() + "点伤害");
            // 吸血: 造成伤害的同时为己方英雄回复等量生命(上限30)
            if (hasKeyword(atk, "lifesteal")) {
                int before = me.getHp();
                me.setHp(Math.min(30, me.getHp() + atk.getAttack()));
                healed += me.getHp() - before;
                log(userId.toString(), atk.getName() + " 吸血: 回复" + (me.getHp() - before) + "点生命");
            }
        } else {
            if (dst < 0 || dst >= foe.getBoard().size()) throw new BizException("非法目标下标");
            Card def = foe.getBoard().get(dst);
            ev.put("targetName", def.getName());
            // 圣盾: 抵消一次伤害, 随后破盾(从 effect 里移除 shield 关键字)
            int dmgToDef = atk.getAttack();
            int dmgToAtk = def.getAttack();
            if (hasKeyword(def, "shield")) {
                dmgToDef = 0;
                removeKeyword(def, "shield");
                shieldBroken = true;
                log(userId.toString(), def.getName() + " 的圣盾被打破, 未受到伤害");
            }
            if (hasKeyword(atk, "shield")) {
                dmgToAtk = 0;
                removeKeyword(atk, "shield");
                shieldBroken = true;
                log(userId.toString(), atk.getName() + " 的圣盾被打破, 未受到反击伤害");
            }
            // 互殴结算: 伤害持久化(未死随从保留扣血后生命), 阵亡触发亡语
            int atkAfter = atk.getHp() - dmgToAtk;
            int defAfter = def.getHp() - dmgToDef;
            // 剧毒: 只要对随从造成了伤害就直接摧毁它, 与伤害数值大小无关
            boolean poisonKillDef = hasKeyword(atk, "poisonous") && dmgToDef > 0;
            boolean poisonKillAtk = hasKeyword(def, "poisonous") && dmgToAtk > 0;
            if (poisonKillDef) defAfter = Math.min(defAfter, 0);
            if (poisonKillAtk) atkAfter = Math.min(atkAfter, 0);
            atk.setHp(atkAfter);
            def.setHp(defAfter);
            attackerDied = atkAfter <= 0;
            targetDied = defAfter <= 0;
            poisonKill = poisonKillDef || poisonKillAtk;
            // 吸血: 对随从造成的伤害同样回血
            if (hasKeyword(atk, "lifesteal") && dmgToDef > 0) {
                int before = me.getHp();
                me.setHp(Math.min(30, me.getHp() + dmgToDef));
                healed += me.getHp() - before;
                log(userId.toString(), atk.getName() + " 吸血: 回复" + (me.getHp() - before) + "点生命");
            }
            // 尸体先按实例移除再触发亡语(防守方亡语AOE可能改变双方场上构成, 按索引删除会错位)
            if (targetDied) foe.getBoard().removeIf(m -> m == def);
            if (attackerDied) me.getBoard().removeIf(m -> m == atk);
            if (targetDied) resolveAbilities(foe, me, def, "deathrattle");
            if (attackerDied) resolveAbilities(me, foe, atk, "deathrattle");
            log(userId.toString(), atk.getName() + " 攻击了 " + def.getName() + ", 造成" + dmgToDef + "点伤害"
                    + (dmgToAtk > 0 ? ", 受到" + dmgToAtk + "点反击" : ""));
            if (poisonKillDef) log(userId.toString(), "剧毒生效:「" + def.getName() + "」被毒素直接摧毁");
            if (poisonKillAtk) log(userId.toString(), "剧毒反噬:「" + atk.getName() + "」中毒阵亡");
        }
        ev.put("attackerDied", attackerDied);
        ev.put("targetDied", targetDied);
        ev.put("heal", healed);               // 吸血回血(>0 时前端播绿色飘字)
        ev.put("shieldBroken", shieldBroken); // 圣盾被击碎(前端播破盾闪光)
        ev.put("poison", poisonKill);         // 剧毒直接摧毁(前端播毒雾特效)
        lastAction = ev;

        // 风怒: 每回合可攻击两次 —— 第一次攻击后不锁定, 第二次才进入 acted
        if (hasKeyword(atk, "windfury") && !me.getWindUsed().contains(atk)) {
            me.getWindUsed().add(atk);
            log(userId.toString(), atk.getName() + " 风怒: 本回合还可再攻击一次");
        } else {
            me.getActed().add(atk);   // 普通随从: 每回合限攻击一次
        }
        checkGameOver();
    }

    /**
     * 英雄技能: 2 费对任意目标(敌方英雄或任意随从)造成 1 点伤害, 每回合限一次
     * 属于法术类效果, 不受「嘲讽」限制, 目标需逐个点击选择
     * @param dst -1=敌方英雄, 否则为敌方场上随从下标
     */
    public synchronized void heroPower(Long userId, int dst) {
        assertMyTurn(userId);
        touch();
        BattlePlayer me = players[current];
        BattlePlayer foe = players[1 - current];
        if (me.isHeroPowerUsed()) throw new BizException("本回合的英雄技能已经使用过");
        if (me.getMana() < HERO_POWER_COST) throw new BizException("法力值不足(英雄技能需要" + HERO_POWER_COST + "点)");
        if (dst >= foe.getBoard().size()) throw new BizException("非法目标下标");
        if (dst != -1 && dst < 0) throw new BizException("非法目标下标");

        me.setMana(me.getMana() - HERO_POWER_COST);
        me.setHeroPowerUsed(true);

        Map<String, Object> ev = new HashMap<>();
        ev.put("seq", ++actionSeq);
        ev.put("type", "heropower");
        ev.put("attackerUserId", userId);
        ev.put("src", -1);
        ev.put("dst", dst);
        ev.put("damage", HERO_POWER_DAMAGE);

        if (dst == -1) {
            ev.put("targetName", "英雄");
            foe.setHp(foe.getHp() - HERO_POWER_DAMAGE);
            log(userId.toString(), "使用英雄技能, 对敌方英雄造成" + HERO_POWER_DAMAGE + "点伤害");
        } else {
            Card def = foe.getBoard().get(dst);
            ev.put("targetName", def.getName());
            if (hasKeyword(def, "shield")) {                 // 圣盾抵消一次技能伤害
                removeKeyword(def, "shield");
                log(userId.toString(), "英雄技能被 " + def.getName() + " 的圣盾挡下");
            } else {
                def.setHp(def.getHp() - HERO_POWER_DAMAGE);
                log(userId.toString(), "使用英雄技能, 对 " + def.getName() + " 造成" + HERO_POWER_DAMAGE + "点伤害");
                if (def.getHp() <= 0) {
                    foe.getBoard().removeIf(m -> m == def);
                    resolveAbilities(foe, me, def, "deathrattle");
                }
            }
        }
        lastAction = ev;
        checkGameOver();
    }

    /** 结束回合 -> 换边: 回合数+1, 最大法力+1(上限10), 法力回满, 抽1张 */
    public synchronized void endTurn(Long userId) {
        assertMyTurn(userId);
        touch();
        current = 1 - current;
        turnNo++;
        if (turnNo > MAX_TURNS) {          // 平局: winnerId=null, 结算时不加分
            log("system", "回合数达到上限" + MAX_TURNS + ", 判定为平局");
            winnerId = null;
            gameOver();
            return;
        }
        beginTurn();
    }

    /** 投降 */
    public synchronized Map<String, Object> surrender(Long userId) {
        if (phase == Phase.GAME_OVER) throw new BizException("对局已结束");
        if (!hasPlayer(userId)) throw new BizException("你不在该对局中");
        winnerId = players[0].getUserId().equals(userId) ? players[1].getUserId() : players[0].getUserId();
        return gameOver();
    }

    /** 该用户是否是本房间的对局者(操作鉴权用: 防止越权操作他人房间) */
    public boolean hasPlayer(Long userId) {
        if (userId == null) return false;
        for (BattlePlayer p : players) {
            if (p.getUserId().equals(userId)) return true;
        }
        return false;
    }

    private void beginTurn() {
        phase = Phase.MAIN;
        BattlePlayer p = players[current];
        p.setMaxMana(Math.min(10, p.getMaxMana() + 1));
        p.setMana(p.getMaxMana());
        p.getActed().clear();     // 新回合: 我方随从恢复可攻击
        p.getSleeping().clear();  // 新回合: 上回合上场的随从解除召唤失调
        p.getWindUsed().clear();   // 新回合: 风怒随从的"第一次攻击"额度恢复
        p.getRushLock().clear();   // 新回合: 突袭随从不再是登场回合, 可以打脸了
        p.setHeroPowerUsed(false); // 新回合: 英雄技能恢复可用
        Card c = p.drawOne();
        if (c != null) {
            p.getHand().add(c);
        } else {
            // 牌库已空: 触发疲劳, 伤害逐次递增(第1次1点、第2次2点...)
            int dmg = p.nextFatigue();
            p.setHp(p.getHp() - dmg);
            log(p.getUserId().toString(),
                    p.getNickname() + " 牌库已空, 受到 " + dmg + " 点疲劳伤害(第" + p.getFatigue() + "层)");
        }
        log(p.getUserId().toString(), "回合" + turnNo + " 开始, 法力=" + p.getMana() + "/" + p.getMaxMana());
        checkGameOver();   // 疲劳可能直接致死后手/先手
    }

    /* ==================== 技能系统 ====================
     * cards.effect 编码: 多条用 ';' 分隔, 单条格式 "时机:效果:数值"
     *  - 主动: battlecry(战吼, 打出时) / deathrattle(亡语, 阵亡时)
     *  - 被动关键字: taunt(嘲讽) / charge(冲锋) / shield(圣盾) / lifesteal(吸血)
     *                / rush(突袭) / windfury(风怒) / poisonous(剧毒)
     * 主动效果: damage_face / heal / draw / aoe / damage_random / buff_all / summon
     * 例: "battlecry:draw:1;taunt:0:0" = 战吼抽一张牌 + 嘲讽
     * ================================================== */

    /** 是否拥有某被动关键字(taunt/charge/shield/lifesteal/rush/windfury/poisonous) */
    private boolean hasKeyword(Card c, String kw) {
        String eff = c.getEffect();
        if (eff == null || eff.isBlank()) return false;
        for (String one : eff.split(";")) {
            String[] p = one.trim().split(":");
            if (p.length >= 1 && p[0].trim().equals(kw)) return true;
        }
        return false;
    }

    /** 移除关键字(圣盾被打碎时用) */
    private void removeKeyword(Card c, String kw) {
        String eff = c.getEffect();
        if (eff == null || eff.isBlank()) return;
        List<String> keep = new ArrayList<>();
        for (String one : eff.split(";")) {
            if (!one.trim().split(":")[0].trim().equals(kw)) keep.add(one);
        }
        c.setEffect(String.join(";", keep));
    }

    /** 结算一张卡在某个时机的全部技能 */
    private void resolveAbilities(BattlePlayer owner, BattlePlayer foe, Card c, String timing) {
        String eff = c.getEffect();
        if (eff == null || eff.isBlank()) return;
        for (String one : eff.split(";")) {
            String[] parts = one.trim().split(":");
            if (parts.length != 3 || !parts[0].trim().equals(timing)) continue;
            resolveOne(owner, foe, c, parts[1].trim(), safeInt(parts[2]), timing);
        }
    }

    private int safeInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    private void resolveOne(BattlePlayer owner, BattlePlayer foe, Card c, String kind, int val, String timing) {
        String label = "battlecry".equals(timing) ? "战吼" : "亡语";
        String actor = owner.getUserId().toString();
        switch (kind) {
            case "damage_face":         // 对敌方英雄造成伤害
                foe.setHp(foe.getHp() - val);
                log(actor, c.getName() + " " + label + ": 对敌方英雄造成" + val + "点伤害");
                break;
            case "heal":                // 治疗己方英雄
                owner.setHp(Math.min(30, owner.getHp() + val));
                log(actor, c.getName() + " " + label + ": 为己方英雄回复" + val + "点生命");
                break;
            case "draw":                // 抽牌(牌库空时同样吃疲劳)
                int got = 0;
                for (int i = 0; i < val; i++) {
                    Card d = owner.drawOne();
                    if (d == null) {
                        int dmg = owner.nextFatigue();
                        owner.setHp(owner.getHp() - dmg);
                        log(actor, c.getName() + " " + label + " 抽牌时牌库已空, 受到" + dmg + "点疲劳伤害");
                        break;
                    }
                    owner.getHand().add(d);
                    got++;
                }
                log(actor, c.getName() + " " + label + ": 抽了" + got + "张牌");
                break;
            case "aoe":                 // 敌方随从全体伤害
                List<Card> died = new ArrayList<>();
                for (Card m : foe.getBoard()) {
                    if (hasKeyword(m, "shield")) { removeKeyword(m, "shield"); continue; }  // 圣盾免疫
                    m.setHp(m.getHp() - val);
                    if (m.getHp() <= 0) died.add(m);
                }
                foe.getBoard().removeIf(m -> m.getHp() <= 0);
                log(actor, c.getName() + " " + label + ": 对所有敌方随从造成" + val + "点伤害"
                        + (died.isEmpty() ? "" : ", 摧毁了" + died.size() + "个随从"));
                for (Card m : died) resolveAbilities(foe, owner, m, "deathrattle");   // 有界连锁: 场上至多7个
                break;
            case "damage_random":       // 随机敌方随从造成伤害
                if (foe.getBoard().isEmpty()) {
                    log(actor, c.getName() + " " + label + ": 敌方没有随从, 技能未生效");
                    break;
                }
                Card pick = foe.getBoard().get(random.nextInt(foe.getBoard().size()));
                if (hasKeyword(pick, "shield")) {
                    removeKeyword(pick, "shield");
                    log(actor, c.getName() + " " + label + ": " + pick.getName() + " 的圣盾挡下了这一击");
                } else {
                    pick.setHp(pick.getHp() - val);
                    boolean dead = pick.getHp() <= 0;
                    log(actor, c.getName() + " " + label + ": 对 " + pick.getName() + " 造成" + val + "点伤害");
                    if (dead) {
                        foe.getBoard().removeIf(m -> m == pick);
                        resolveAbilities(foe, owner, pick, "deathrattle");
                    }
                }
                break;
            case "buff_all":            // 己方其他随从 +val/+val
                int n = 0;
                for (Card m : owner.getBoard()) {
                    if (m == c) continue;
                    m.setAttack(m.getAttack() + val);
                    m.setHp(m.getHp() + val);
                    n++;
                }
                log(actor, c.getName() + " " + label + ": " + n + "个友方随从获得 +" + val + "/+" + val);
                break;
            case "summon":              // 召唤衍生物(1/1 战术傀儡); 衍生物同样召唤失调, 下回合才能攻击
                int made = 0;
                for (int i = 0; i < val && owner.getBoard().size() < 7; i++) {
                    Card t = token();
                    owner.getBoard().add(t);
                    owner.getSleeping().add(t);
                    made++;
                }
                log(actor, c.getName() + " " + label + ": 召唤了" + made + "个战术傀儡(1/1)");
                break;
            default:
                break;
        }
        checkGameOver();
    }

    /** 衍生物: 1 费 1/1 战术傀儡(不入库, 仅存在于本局战场) */
    private Card token() {
        Card t = new Card();
        t.setId(-1L);
        t.setName("战术傀儡");
        t.setCost(1);
        t.setAttack(1);
        t.setHp(1);
        t.setRarity(1);
        t.setImg("/cards/25.jpg");   // 复用 12F 的立绘当衍生物
        t.setEffect("");
        return t;
    }

    private void checkGameOver() {
        if (phase == Phase.GAME_OVER) return;
        BattlePlayer a = players[0], b = players[1];
        if (a.getHp() <= 0 || b.getHp() <= 0) {
            winnerId = a.getHp() <= 0 ? b.getUserId() : a.getUserId();
            gameOver();
        }
    }

    private Map<String, Object> gameOver() {
        phase = Phase.GAME_OVER;
        log("system", "游戏结束! 胜者=" + winnerId);
        Map<String, Object> r = new HashMap<>();
        r.put("event", "game_over");
        r.put("winnerId", winnerId);
        r.put("snapshot", snapshot());
        return r;
    }

    private void assertMyTurn(Long userId) {
        if (phase == Phase.GAME_OVER) throw new BizException("对局已结束");
        if (!players[current].getUserId().equals(userId)) throw new BizException("还没轮到你行动");
    }

    /**
     * 写一条战斗日志。
     * seq 为全局递增序号: 快照只下发最后 10 条(logTail), 前端靠 seq 去重即可
     * 把多次收到的 tail 拼成完整历史, 用于左侧"炉石式"滚动日志面板。
     */
    private void log(String actor, String text) {
        Map<String, Object> m = new HashMap<>();
        m.put("seq", ++logSeq);
        m.put("actor", actor);
        m.put("text", text);
        m.put("turn", turnNo);
        log.add(m);
    }

    /** 当前局面快照(公开信息+logTail), game_over场景用, 手牌双方均隐藏 */
    public Map<String, Object> snapshot() {
        return snapshotFor(null);
    }

    /**
     * 按观察者定制的私密快照: 自己看全手牌, 对手只发handCount数量
     * 防作弊点(论文可写): 快照随观察者变化, 杜绝客户端互相嗅探手牌
     */
    public Map<String, Object> snapshotFor(Long viewerId) {
        Map<String, Object> r = new HashMap<>();
        r.put("event", "state");
        r.put("roomId", roomId);
        r.put("phase", phase.name());
        r.put("turnNo", turnNo);
        r.put("currentUserId", players[current].getUserId());
        r.put("winnerId", winnerId);
        r.put("p1", pub(players[0], players[0].getUserId().equals(viewerId)));
        r.put("p2", pub(players[1], players[1].getUserId().equals(viewerId)));
        r.put("lastAction", lastAction);
        r.put("logTail", log.subList(Math.max(0, log.size() - 10), log.size()));
        // 房间回收倒计时信息: 前端可据此提示"长时间无操作, 本局将在 Xs 后判和"
        r.put("idleSec", (int) (idleMillis() / 1000));
        r.put("idleTimeoutSec", (int) (IDLE_TIMEOUT_MS / 1000));
        return r;
    }

    /**
     * 观战视图: 只含"双方都能看到的信息", 任何一方的一手牌都不下发。
     * 用于 /topic/battle/{roomId} 观战频道 —— 观众可以看到场上局势与战斗日志, 但看不到手牌。
     */
    public Map<String, Object> spectatorView() {
        Map<String, Object> r = new HashMap<>();
        r.put("event", "spectate");
        r.put("roomId", roomId);
        r.put("phase", phase.name());
        r.put("turnNo", turnNo);
        r.put("currentUserId", players[current].getUserId());
        r.put("winnerId", winnerId);
        r.put("p1", pubSpectator(players[0]));
        r.put("p2", pubSpectator(players[1]));
        r.put("lastAction", lastAction);
        r.put("logTail", log.subList(Math.max(0, log.size() - 10), log.size()));
        return r;
    }

    private Map<String, Object> pubSpectator(BattlePlayer p) {
        Map<String, Object> m = new HashMap<>();
        m.put("userId", p.getUserId());
        m.put("nickname", p.getNickname());
        m.put("hp", p.getHp());
        m.put("mana", p.getMana());
        m.put("maxMana", p.getMaxMana());
        m.put("board", p.getBoard());
        m.put("deckCount", p.getDeck().size());
        m.put("handCount", p.getHand().size());      // 只给张数, 不给牌面 —— 观战不得泄露手牌
        m.put("fatigue", p.getFatigue());
        m.put("heroPowerUsed", p.isHeroPowerUsed());
        m.put("offline", p.getOfflineSince() != null);
        // 观战视角不下发"本回合可否攻击/是否召唤失调"(属于当前行动方的决策信息)
        for (Card c : p.getBoard()) { c.setSick(null); c.setCanAct(null); c.setRushLock(null); }
        return m;
    }

    private Map<String, Object> pub(BattlePlayer p, boolean self) {
        Map<String, Object> m = new HashMap<>();
        m.put("userId", p.getUserId());
        m.put("nickname", p.getNickname());
        m.put("hp", p.getHp());
        m.put("mana", p.getMana());
        m.put("maxMana", p.getMaxMana());
        m.put("board", p.getBoard());
        m.put("deckCount", p.getDeck().size());
        m.put("heroPowerUsed", p.isHeroPowerUsed());
        m.put("heroPowerCost", HERO_POWER_COST);
        m.put("offline", p.getOfflineSince() != null);
        m.put("fatigue", p.getFatigue());   // 疲劳层数(牌库抽空后递增, 下次抽牌将受 n 点伤害)
        if (self) {
            // 我方随从下发本回合可否攻击(召唤失调/已行动灰显)
            boolean acting = players[current].getUserId().equals(p.getUserId());
            for (Card c : p.getBoard()) {
                boolean sick = p.getSleeping().contains(c);
                c.setSick(acting && sick);
                c.setCanAct(acting && !sick && !p.getActed().contains(c));
                c.setRushLock(acting && p.getRushLock().contains(c));   // 突袭登场回合: 不可打脸
            }
            m.put("hand", p.getHand());
        } else {
            // 对手手牌一律不下发(连空数组都不给, 避免协议歧义与客户端误判)
            m.put("handCount", p.getHand().size());
            // 对手视角同样不下发"是否召唤失调/能否行动/是否突袭锁定"
            // —— Card 是共享实例, 不清掉会把上一次给本人构造的瞬态字段一并序列化出去
            for (Card c : p.getBoard()) { c.setSick(null); c.setCanAct(null); c.setRushLock(null); }
        }
        return m;
    }

    /**
     * 场上「嘲讽」随从的下标列表(AI 决策用)。
     * 没有它, AI 只能靠"试着打脸、被打回"来发现嘲讽, 一旦攻击被拒就会中断回合导致对局卡死。
     */
    public List<Integer> tauntIdx(BattlePlayer p) {
        List<Integer> r = new ArrayList<>();
        List<Card> b = p.getBoard();
        for (int i = 0; i < b.size(); i++) {
            if (hasKeyword(b.get(i), "taunt")) r.add(i);
        }
        return r;
    }
}
