package com.cardbattle.service;

import com.cardbattle.dto.DeckDTO;
import com.cardbattle.entity.UserDeck;

import java.util.List;
import java.util.Map;

public interface DeckService {
    Long saveDeck(Long userId, DeckDTO dto);
    List<UserDeck> myList(Long userId);
    void delete(Long userId, Long deckId);
    UserDeck getDefault(Long userId);   // 匹配时取出战卡组(没有则最新一套), 都没有则随机组卡

    /** 设为出战卡组: 同用户其它卡组自动取消出战 */
    void setDefault(Long userId, Long deckId);

    /** 一键生成: 用已解锁卡牌自动凑一副合规的 30 张卡组(没有卡组 / 卡组不合规时使用) */
    Long autoDeck(Long userId);

    /** 卡组是否合规: 恰好 30 张, 且全部是已解锁的真实卡牌 */
    boolean deckValid(Long userId, UserDeck d);

    /** 生成卡组分享码(导出) */
    Map<String, Object> exportCode(Long userId, Long deckId);

    /** 导入卡组分享码: 缺失卡牌自动用已拥有的卡补齐到 30 张 */
    Map<String, Object> importCode(Long userId, String code, String name);
}
