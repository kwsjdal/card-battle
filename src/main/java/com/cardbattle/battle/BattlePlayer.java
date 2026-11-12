package com.cardbattle.battle;

import com.cardbattle.entity.Card;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/** 战斗中的一名玩家状态 */
@Data
public class BattlePlayer {
    private Long userId;
    private String nickname;
    private List<Card> deck = new ArrayList<>();        // 牌库
    /** 本局所用卡组id(随机卡组/机器人没有, 结算时写进战报用于卡组胜率统计) */
    private Long deckId;
    private LinkedList<Card> hand = new LinkedList<>(); // 手牌
    private List<Card> board = new ArrayList<>();       // 场上随从
    private int hp = 30;
    private int mana = 0;      // 首发0, beginTurn 首回合涨到1
    private int maxMana = 0;
    /** 本回合已攻击过的随从(实例级判重, 每随从每回合限一次) */
    private final Set<Card> acted = Collections.newSetFromMap(new IdentityHashMap<>());
    /**
     * 召唤失调: 本回合刚上场(打出/被召唤)且没有「冲锋」的随从, 下个回合才能攻击。
     * 与 acted 分开记, 这样提示语能区分「召唤失调」和「本回合已攻击过」。
     */
    private final Set<Card> sleeping = Collections.newSetFromMap(new IdentityHashMap<>());
    /**
     * 风怒: 本回合已经用掉"第一次攻击"的随从。
     * 拥有 windfury 的随从每回合可攻击两次, 第一次攻击后不进入 acted, 而是先进这里。
     */
    private final Set<Card> windUsed = Collections.newSetFromMap(new IdentityHashMap<>());
    /**
     * 突袭锁定: 本回合刚上场、拥有 rush 的随从。
     * 突袭不受召唤失调影响, 但登场当回合只能攻击敌方随从, 不能攻击英雄。
     */
    private final Set<Card> rushLock = Collections.newSetFromMap(new IdentityHashMap<>());
    /** 本回合是否已使用英雄技能(每回合限一次) */
    private boolean heroPowerUsed = false;
    /** 掉线起始时间戳(毫秒), null=在线; 用于"断线重连宽限期", 宽限期内重连不判负 */
    private volatile Long offlineSince;

    /**
     * 疲劳层数: 牌库抽空后每次抽牌都会递增, 第 n 次疲劳抽牌对自己造成 n 点伤害。
     * 作用(论文可写): 让对局必然收敛 —— 没有疲劳时双方牌打光会陷入"干瞪眼",
     * 只能靠 45 分钟总时长上限兜底; 有了疲劳, 疲劳伤害递增会强制分出胜负。
     */
    private int fatigue = 0;

    /** 抽一张牌, 牌库空则返回 null(由调用方结算疲劳) */
    public Card drawOne() {
        if (deck.isEmpty()) return null;
        return deck.remove(0);
    }

    /** 记一次疲劳并返回本次应承受的伤害(1,2,3,...) */
    public int nextFatigue() {
        return ++fatigue;
    }
}
