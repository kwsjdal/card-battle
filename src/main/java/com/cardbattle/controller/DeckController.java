package com.cardbattle.controller;

import com.cardbattle.common.Result;
import com.cardbattle.dto.DeckDTO;
import com.cardbattle.entity.UserDeck;
import com.cardbattle.service.DeckService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/decks")
@RequiredArgsConstructor
public class DeckController {

    private final DeckService deckService;

    @GetMapping
    public Result<List<UserDeck>> myList(HttpServletRequest req) {
        return Result.ok(deckService.myList(uid(req)));
    }

    @PostMapping
    public Result<Long> save(@RequestBody @Validated DeckDTO dto, HttpServletRequest req) {
        return Result.ok(deckService.saveDeck(uid(req), dto));
    }

    /** 一键生成合规卡组: 用已解锁卡牌凑满 30 张(没有卡组/卡组不合规时用) */
    @PostMapping("/auto")
    public Result<Long> auto(HttpServletRequest req) {
        return Result.ok(deckService.autoDeck(uid(req)));
    }

    /** 设为出战卡组(匹配对局时使用) */
    @PostMapping("/default")
    public Result<Void> setDefault(@RequestBody java.util.Map<String, Object> body, HttpServletRequest req) {
        Object id = body.get("id");
        if (id == null) throw new com.cardbattle.common.BizException("缺少卡组 id");
        deckService.setDefault(uid(req), Long.valueOf(String.valueOf(id)));
        return Result.ok();
    }

    /** 导出卡组分享码: /api/decks/code?deckId=1 */
    @GetMapping("/code")
    public Result<Map<String, Object>> exportCode(@RequestParam Long deckId, HttpServletRequest req) {
        return Result.ok(deckService.exportCode(uid(req), deckId));
    }

    /** 导入卡组分享码: {code, name?} */
    @PostMapping("/code")
    public Result<Map<String, Object>> importCode(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        String code = String.valueOf(body.getOrDefault("code", "")).trim();
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        if (code.isEmpty()) throw new com.cardbattle.common.BizException("请输入卡组分享码");
        return Result.ok(deckService.importCode(uid(req), code, name));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest req) {
        deckService.delete(uid(req), id);
        return Result.ok();
    }

    private Long uid(HttpServletRequest req) {
        return (Long) req.getAttribute("userId");
    }
}
