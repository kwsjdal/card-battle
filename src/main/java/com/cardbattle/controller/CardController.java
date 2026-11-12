package com.cardbattle.controller;

import com.cardbattle.common.Result;
import com.cardbattle.entity.Card;
import com.cardbattle.service.GachaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
public class CardController {

    private final com.cardbattle.service.CardCache cardCache;
    private final GachaService gachaService;

    /** 卡牌库: 仅返回当前玩家已解锁的卡(卡组编辑页用), 卡池走本地缓存 */
    @GetMapping
    public Result<List<Card>> all(HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        Set<Long> owned = gachaService.ownedIds(userId);
        List<Card> list = cardCache.all().stream()
                .filter(c -> owned.contains(c.getId()))
                .sorted(Comparator.comparing(Card::getCost).thenComparing(Card::getId))
                .collect(Collectors.toList());
        return Result.ok(list);
    }
}
