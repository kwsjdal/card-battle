package com.cardbattle.service;

import com.cardbattle.entity.Card;
import com.cardbattle.mapper.CardMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * 卡池缓存: cards 表属于静态低频数据, 避免抽卡/组卡/卡库/数据中心每次都全表查询 + 重新加工。
 *
 * 缓存内容(均在刷新时一次性算好, 请求时只做读取):
 *  1. 卡列表 —— 供全量遍历
 *  2. 按稀有度分组 —— 抽卡按稀有度roll时直接用, 免掉每次 pull 都 groupingBy 一遍 66 张卡
 *  3. 静态字段视图 —— 66 张 × 10 个字段的结果 Map 预先建好, 请求时只补 per-user 的 owned 字段
 *
 * 每60s自动刷新, 管理端改卡后最迟60s生效(重启或等刷新即可)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardCache {

    private final CardMapper cardMapper;
    private volatile List<Card> pool = List.of();
    private volatile Map<Integer, List<Card>> byRarity = Map.of();
    private volatile List<Map<String, Object>> staticViews = List.of();

    @PostConstruct
    public void init() {
        refresh();
    }

    @Scheduled(fixedDelay = 60_000)
    public void refresh() {
        List<Card> all = cardMapper.selectList(null);
        pool = Collections.unmodifiableList(all);

        Map<Integer, List<Card>> g = new HashMap<>();
        for (Card c : all) {
            g.computeIfAbsent(c.getRarity() == null ? 1 : c.getRarity(), k -> new ArrayList<>()).add(c);
        }
        byRarity = Collections.unmodifiableMap(g);

        List<Map<String, Object>> views = new ArrayList<>(all.size());
        for (Card c : all) {
            Map<String, Object> m = new HashMap<>(11);
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("cost", c.getCost());
            m.put("attack", c.getAttack());
            m.put("hp", c.getHp());
            m.put("rarity", c.getRarity());
            m.put("img", c.getImg());
            m.put("description", c.getDescription());
            m.put("effect", c.getEffect());
            views.add(Collections.unmodifiableMap(m));
        }
        staticViews = Collections.unmodifiableList(views);

        log.debug("卡池缓存已刷新, 共{}张, 稀有度分组{}档", all.size(), g.size());
    }

    /** 只读视图: 调用方不得修改返回列表; 需要独立实例时请使用 Card.copy() */
    public List<Card> all() {
        return pool;
    }

    /** 按稀有度分组(抽卡时直接使用, 无需每次重新分组) */
    public Map<Integer, List<Card>> byRarity() {
        return byRarity;
    }

    /** 静态字段视图列表(不含 per-user 字段), 调用方需自行 new HashMap<>(view) 后再补字段 */
    public List<Map<String, Object>> staticViews() {
        return staticViews;
    }
}
