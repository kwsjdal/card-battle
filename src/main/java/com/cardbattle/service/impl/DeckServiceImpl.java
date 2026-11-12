package com.cardbattle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cardbattle.common.BizException;
import com.cardbattle.dto.DeckDTO;
import com.cardbattle.entity.Card;
import com.cardbattle.entity.UserCard;
import com.cardbattle.entity.UserDeck;
import com.cardbattle.mapper.CardMapper;
import com.cardbattle.mapper.UserCardMapper;
import com.cardbattle.mapper.UserDeckMapper;
import com.cardbattle.service.DeckService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeckServiceImpl implements DeckService {

    private final UserDeckMapper deckMapper;
    private final CardMapper cardMapper;
    private final UserCardMapper userCardMapper;
    private final ObjectMapper objectMapper;

    @Override
    public Long saveDeck(Long userId, DeckDTO dto) {
        List<Long> ids = dto.getCardIds();
        if (ids == null || ids.size() != 30) throw new BizException("卡组必须恰好30张");

        // 只能使用已解锁的卡牌
        Set<Long> owned = userCardMapper.selectList(new LambdaQueryWrapper<UserCard>()
                        .eq(UserCard::getUserId, userId))
                .stream().map(UserCard::getCardId).collect(Collectors.toSet());
        if (!owned.containsAll(ids)) throw new BizException("卡组中存在尚未解锁的卡牌");

        // 同名卡上限: 已解锁N种时上限 max(2, ceil(30/N)), 保证能凑满30张
        int perLimit = Math.max(2, (int) Math.ceil(30.0 / Math.max(owned.size(), 1)));
        var same = ids.stream().collect(Collectors.groupingBy(i -> i, Collectors.counting()));
        if (same.values().stream().anyMatch(c -> c > perLimit))
            throw new BizException("同名卡牌最多携带" + perLimit + "张");
        // 卡牌必须真实存在
        if (cardMapper.selectBatchIds(ids).size() != same.size())
            throw new BizException("卡组中存在不存在的卡牌");

        UserDeck deck = new UserDeck();
        deck.setUserId(userId);
        deck.setName(dto.getName());
        try {
            deck.setCardIds(objectMapper.writeValueAsString(ids));
        } catch (Exception e) {
            throw new BizException("卡组保存失败");
        }
        if (dto.getId() != null) {
            UserDeck old = deckMapper.selectById(dto.getId());
            if (old == null || !old.getUserId().equals(userId)) throw new BizException("卡组不存在");
            deck.setId(dto.getId());
            deckMapper.updateById(deck);
        } else {
            deckMapper.insert(deck);
        }
        return deck.getId();
    }

    @Override
    public List<UserDeck> myList(Long userId) {
        return deckMapper.selectList(new LambdaQueryWrapper<UserDeck>()
                .eq(UserDeck::getUserId, userId).orderByDesc(UserDeck::getId));
    }

    @Override
    public void delete(Long userId, Long deckId) {
        UserDeck d = deckMapper.selectById(deckId);
        if (d == null || !d.getUserId().equals(userId)) throw new BizException("卡组不存在");
        deckMapper.deleteById(deckId);
    }

    @Override
    public boolean deckValid(Long userId, UserDeck d) {
        List<Long> ids = readIds(d);
        if (ids.isEmpty() || ids.size() != 30) return false;
        Set<Long> owned = userCardMapper.selectList(new LambdaQueryWrapper<UserCard>()
                        .eq(UserCard::getUserId, userId))
                .stream().map(UserCard::getCardId).collect(Collectors.toSet());
        if (!owned.containsAll(ids)) return false;
        return cardMapper.selectBatchIds(ids.stream().distinct().collect(Collectors.toList())).size()
                == ids.stream().distinct().count();
    }

    @Override
    public Long autoDeck(Long userId) {
        List<Long> owned = userCardMapper.selectList(new LambdaQueryWrapper<UserCard>()
                        .eq(UserCard::getUserId, userId))
                .stream().map(UserCard::getCardId).distinct().collect(Collectors.toList());
        if (owned.isEmpty()) throw new BizException("还没有任何卡牌, 请先到商店抽卡");

        // 按费用从低到高排, 铺出来的曲线更合理
        Map<Long, Integer> cost = cardMapper.selectBatchIds(owned).stream()
                .collect(Collectors.toMap(Card::getId, c -> c.getCost() == null ? 0 : c.getCost()));
        owned.sort(Comparator.comparingLong(id -> cost.getOrDefault(id, 0)));

        int perLimit = Math.max(2, (int) Math.ceil(30.0 / owned.size()));
        List<Long> ids = new ArrayList<>();
        for (int round = 0; round < perLimit && ids.size() < 30; round++) {
            for (Long id : owned) {
                if (ids.size() >= 30) break;
                ids.add(id);
            }
        }
        if (ids.size() < 30) throw new BizException("已解锁卡牌不足, 无法凑满 30 张, 请先抽卡");

        // 没有卡组就顺带设为出战卡组
        boolean first = deckMapper.selectCount(new LambdaQueryWrapper<UserDeck>()
                .eq(UserDeck::getUserId, userId)) == 0;
        UserDeck deck = new UserDeck();
        deck.setUserId(userId);
        deck.setName(first ? "新手推荐卡组" : "自动推荐卡组");
        try {
            deck.setCardIds(objectMapper.writeValueAsString(ids));
        } catch (Exception e) {
            throw new BizException("卡组生成失败");
        }
        deck.setIsDefault(first ? 1 : 0);
        deckMapper.insert(deck);
        return deck.getId();
    }

    /** 解析卡组里的卡牌 id 列表; 解析失败返回空列表 */
    private List<Long> readIds(UserDeck d) {
        if (d == null || d.getCardIds() == null) return new ArrayList<>();
        try {
            List<Long> ids = objectMapper.readValue(d.getCardIds(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class));
            return ids == null ? new ArrayList<>() : ids;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** 用户已解锁的卡牌 id 集合 */
    private Set<Long> ownedIds(Long userId) {
        return userCardMapper.selectList(new LambdaQueryWrapper<UserCard>().eq(UserCard::getUserId, userId))
                .stream().map(UserCard::getCardId).collect(Collectors.toSet());
    }

    @Override
    public Map<String, Object> exportCode(Long userId, Long deckId) {
        UserDeck d = deckMapper.selectById(deckId);
        if (d == null || !d.getUserId().equals(userId)) throw new BizException("卡组不存在");
        List<Long> ids = readIds(d);
        if (ids.size() != 30) throw new BizException("该卡组不是 30 张, 无法生成分享码");
        Map<String, Object> r = new HashMap<>();
        r.put("code", com.cardbattle.util.DeckCodeUtil.encode(ids));
        r.put("name", d.getName());
        r.put("count", ids.size());
        return r;
    }

    /**
     * 导入卡组分享码: 卡牌缺失时用已拥有的卡自动补齐到 30 张(并提示替换了几张)。
     * 这样"抄作业"不会因为没解锁而直接失败。
     */
    @Override
    public Map<String, Object> importCode(Long userId, String code, String name) {
        Map<Long, Integer> want = com.cardbattle.util.DeckCodeUtil.decode(code);
        Set<Long> owned = ownedIds(userId);
        if (owned.isEmpty()) throw new BizException("你还没有任何卡牌, 请先到商店抽卡");

        List<Long> ids = new ArrayList<>();
        List<Long> missing = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : want.entrySet()) {
            if (owned.contains(e.getKey())) {
                for (int i = 0; i < e.getValue(); i++) ids.add(e.getKey());
            } else {
                missing.add(e.getKey());
            }
        }
        int replaced = 30 - ids.size();
        if (replaced > 0) {
            List<Long> ownedList = new ArrayList<>(owned);
            for (Long id : ownedList) {                 // 先用卡组里没有的卡各补 1 张
                if (ids.size() >= 30) break;
                if (!want.containsKey(id)) ids.add(id);
            }
            int perLimit = Math.max(2, (int) Math.ceil(30.0 / ownedList.size()));
            for (int r = 1; r < perLimit && ids.size() < 30; r++) {   // 还不够则按同名上限继续补
                for (Long id : ownedList) {
                    if (ids.size() >= 30) break;
                    ids.add(id);
                }
            }
        }
        Collections.sort(ids);
        if (ids.size() != 30) throw new BizException("补齐后仍不足 30 张, 请先抽卡解锁更多卡牌");

        String deckName = (name == null || name.trim().isEmpty())
                ? "导入卡组 " + LocalDate.now().format(DateTimeFormatter.ofPattern("MM-dd"))
                : name.trim();
        // 首套卡组自动设为出战卡组
        boolean first = deckMapper.selectCount(new LambdaQueryWrapper<UserDeck>()
                .eq(UserDeck::getUserId, userId)) == 0;
        UserDeck deck = new UserDeck();
        deck.setUserId(userId);
        deck.setName(deckName);
        try {
            deck.setCardIds(objectMapper.writeValueAsString(ids));
        } catch (Exception e) {
            throw new BizException("卡组保存失败");
        }
        deck.setIsDefault(first ? 1 : 0);
        deckMapper.insert(deck);

        List<String> missingNames = missing.isEmpty() ? new ArrayList<>()
                : cardMapper.selectBatchIds(missing).stream().map(Card::getName).collect(Collectors.toList());
        Map<String, Object> r = new HashMap<>();
        r.put("deckId", deck.getId());
        r.put("name", deckName);
        r.put("total", ids.size());
        r.put("replaced", replaced);
        r.put("missingCount", missing.size());
        r.put("missingNames", missingNames);
        r.put("setDefault", first ? 1 : 0);
        return r;
    }

    @Override
    public UserDeck getDefault(Long userId) {
        List<UserDeck> list = myList(userId);
        if (!list.isEmpty()) {
            // 优先出战卡组, 没指定则用最新一套
            return list.stream().filter(d -> Integer.valueOf(1).equals(d.getIsDefault()))
                    .findFirst().orElse(list.get(0));
        }
        return null;   // 没有卡组: 由 MatchService 随机组卡兜底
    }

    @Override
    public void setDefault(Long userId, Long deckId) {
        UserDeck d = deckMapper.selectById(deckId);
        if (d == null || !d.getUserId().equals(userId)) throw new BizException("卡组不存在");
        // 先把该用户所有卡组取消出战
        List<UserDeck> all = myList(userId);
        for (UserDeck o : all) {
            if (Integer.valueOf(1).equals(o.getIsDefault())) {
                o.setIsDefault(0);
                deckMapper.updateById(o);
            }
        }
        d.setIsDefault(1);
        deckMapper.updateById(d);
    }
}
