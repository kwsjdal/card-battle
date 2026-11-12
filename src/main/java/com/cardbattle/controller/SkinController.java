package com.cardbattle.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cardbattle.common.BizException;
import com.cardbattle.common.Result;
import com.cardbattle.entity.Skin;
import com.cardbattle.entity.User;
import com.cardbattle.entity.UserOperatorSkin;
import com.cardbattle.entity.UserSkin;
import com.cardbattle.mapper.SkinMapper;
import com.cardbattle.mapper.UserMapper;
import com.cardbattle.mapper.UserSkinMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/** 皮肤商店: 纯外观, 不影响对战数值 */
@RestController
@RequestMapping("/api/skins")
@RequiredArgsConstructor
public class SkinController {

    private final SkinMapper skinMapper;
    private final UserSkinMapper userSkinMapper;
    private final UserMapper userMapper;
    private final com.cardbattle.mapper.UserOperatorSkinMapper operSkinMapper;

    /**
     * 皮肤列表 + 拥有/装配状态
     * 明日方舟模式: 皮肤不是"全局共享"的, 每个干员独立穿着一款,
     * 因此这里返回的是"当前有哪些干员正在穿这款皮肤"(wornBy), 而不是单一的全局装配态。
     */
    @GetMapping
    public Result<Map<String, Object>> list(HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        User u = userMapper.selectById(userId);
        Set<Integer> owned = userSkinMapper.selectList(new LambdaQueryWrapper<UserSkin>()
                        .eq(UserSkin::getUserId, userId))
                .stream().map(UserSkin::getSkinId).collect(Collectors.toSet());
        // 干员 -> 皮肤id 映射, 用于反查"这款皮肤被哪些干员穿着"
        Map<Integer, List<String>> wornBy = new HashMap<>();
        for (UserOperatorSkin e : operSkinMapper.selectList(
                new LambdaQueryWrapper<UserOperatorSkin>().eq(UserOperatorSkin::getUserId, userId))) {
            wornBy.computeIfAbsent(e.getSkinId(), k -> new ArrayList<>()).add(e.getOperator());
        }
        int equipped = u == null ? 0 : u.getSkinId();
        List<Map<String, Object>> list = skinMapper.selectList(null).stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId());
            m.put("name", s.getName());
            m.put("price", s.getPrice());
            m.put("frameClass", s.getFrameClass());
            m.put("description", s.getDescription());
            m.put("operator", s.getOperator());
            m.put("exclusive", s.getOperator() != null);
            m.put("owned", s.getPrice() == 0 || owned.contains(s.getId()));
            m.put("wornBy", wornBy.getOrDefault(s.getId(), Collections.emptyList()));
            return m;
        }).collect(Collectors.toList());
        Map<String, Object> r = new HashMap<>();
        r.put("skins", list);
        r.put("balance", u == null ? 0 : u.getBalance());
        r.put("crystal", u == null || u.getCrystal() == null ? 0 : u.getCrystal());
        r.put("equipped", equipped);
        return Result.ok(r);
    }

    /**
     * 某干员可用皮肤: 该干员的专属时装 + 全部通用卡框
     * GET /api/skins/operator?name=阿米娅
     */
    @GetMapping("/operator")
    public Result<Map<String, Object>> forOperator(HttpServletRequest req,
                                                   @RequestParam(defaultValue = "") String name) {
        Long userId = (Long) req.getAttribute("userId");
        User u = userMapper.selectById(userId);
        Set<Integer> owned = userSkinMapper.selectList(new LambdaQueryWrapper<UserSkin>()
                        .eq(UserSkin::getUserId, userId))
                .stream().map(UserSkin::getSkinId).collect(Collectors.toSet());
        // 该干员当前穿着的皮肤(取每干员独立映射, 而非全局)
        UserOperatorSkin cur = operSkinMapper.selectOne(new LambdaQueryWrapper<UserOperatorSkin>()
                .eq(UserOperatorSkin::getUserId, userId).eq(UserOperatorSkin::getOperator, name));
        final int equipped = cur == null ? 1 : cur.getSkinId();

        List<Map<String, Object>> list = skinMapper.selectList(null).stream()
                .filter(s -> s.getOperator() == null || name.equals(s.getOperator()))
                .map(s -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", s.getId());
                    m.put("name", s.getName());
                    m.put("price", s.getPrice());
                    m.put("frameClass", s.getFrameClass());
                    m.put("description", s.getDescription());
                    m.put("operator", s.getOperator());
                    m.put("exclusive", s.getOperator() != null);
                    m.put("owned", s.getPrice() == 0 || owned.contains(s.getId()));
                    m.put("equipped", equipped == s.getId());
                    return m;
                }).collect(Collectors.toList());

        Map<String, Object> r = new HashMap<>();
        r.put("skins", list);
        r.put("balance", u == null ? 0 : u.getBalance());
        r.put("crystal", u == null || u.getCrystal() == null ? 0 : u.getCrystal());
        r.put("equipped", equipped);
        return Result.ok(r);
    }

    /** 每干员装配映射: {干员名: 皮肤id}, 无记录 = 罗德岛制式 */
    @GetMapping("/map")
    public Result<Map<String, Integer>> myMap(HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        Map<String, Integer> m = new HashMap<>();
        for (UserOperatorSkin e : operSkinMapper.selectList(
                new LambdaQueryWrapper<UserOperatorSkin>().eq(UserOperatorSkin::getUserId, userId))) {
            m.put(e.getOperator(), e.getSkinId());
        }
        return Result.ok(m);
    }

    /**
     * 给指定干员装配皮肤(明日方舟模式: 每个干员独立, 一人一款, 覆盖旧装配)
     * body: {operator: "阿米娅", skinId: 7}
     */
    @PostMapping("/equip-operator")
    @Transactional
    public Result<Map<String, Object>> equipOperator(HttpServletRequest req, @RequestBody Map<String, Object> body) {
        Long userId = (Long) req.getAttribute("userId");
        String operator = String.valueOf(body.getOrDefault("operator", ""));
        Object sidObj = body.get("skinId");
        int skinId = (sidObj instanceof Number) ? ((Number) sidObj).intValue() : 0;
        if (operator.isEmpty()) throw new BizException("缺少干员名");

        Skin s = skinMapper.selectById(skinId);
        if (s == null) throw new BizException("皮肤不存在");
        // 专属时装只能给绑定干员穿; 通用卡框所有干员都能穿
        if (s.getOperator() != null && !s.getOperator().equals(operator))
            throw new BizException("「" + s.getName() + "」是 " + s.getOperator() + " 的专属时装");
        if (s.getPrice() > 0) {
            Long cnt = userSkinMapper.selectCount(new LambdaQueryWrapper<UserSkin>()
                    .eq(UserSkin::getUserId, userId).eq(UserSkin::getSkinId, skinId));
            if (cnt == null || cnt == 0) throw new BizException("尚未拥有该皮肤, 请先购买");
        }

        UserOperatorSkin row = operSkinMapper.selectOne(new LambdaQueryWrapper<UserOperatorSkin>()
                .eq(UserOperatorSkin::getUserId, userId).eq(UserOperatorSkin::getOperator, operator));
        if (row == null) {
            row = new UserOperatorSkin();
            row.setUserId(userId);
            row.setOperator(operator);
            row.setSkinId(skinId);
            operSkinMapper.insert(row);
        } else {
            row.setSkinId(skinId);
            operSkinMapper.updateById(row);
        }

        Map<String, Object> r = new HashMap<>();
        r.put("operator", operator);
        r.put("skinId", skinId);
        r.put("frameClass", s.getFrameClass());
        return Result.ok(r);
    }

    /** 卸下某干员的皮肤(恢复罗德岛制式) */
    @PostMapping("/unequip-operator")
    public Result<Void> unequipOperator(HttpServletRequest req, @RequestBody Map<String, Object> body) {
        Long userId = (Long) req.getAttribute("userId");
        String operator = String.valueOf(body.getOrDefault("operator", ""));
        operSkinMapper.delete(new LambdaQueryWrapper<UserOperatorSkin>()
                .eq(UserOperatorSkin::getUserId, userId).eq(UserOperatorSkin::getOperator, operator));
        return Result.ok();
    }

    /** 购买皮肤 */
    @PostMapping("/buy")
    @Transactional
    public Result<Integer> buy(HttpServletRequest req, @RequestBody Map<String, Integer> body) {
        Long userId = (Long) req.getAttribute("userId");
        int skinId = body.getOrDefault("skinId", 0);
        Skin s = skinMapper.selectById(skinId);
        if (s == null) throw new BizException("皮肤不存在");
        if (s.getPrice() == 0) throw new BizException("该皮肤为默认皮肤");
        Long cnt = userSkinMapper.selectCount(new LambdaQueryWrapper<UserSkin>()
                .eq(UserSkin::getUserId, userId).eq(UserSkin::getSkinId, skinId));
        if (cnt != null && cnt > 0) throw new BizException("已拥有该皮肤");

        User u = userMapper.selectById(userId);
        if (u.getBalance() < s.getPrice()) throw new BizException("金币不足, 请先充值");
        u.setBalance(u.getBalance() - s.getPrice());
        userMapper.updateById(u);

        UserSkin us = new UserSkin();
        us.setUserId(userId);
        us.setSkinId(skinId);
        userSkinMapper.insert(us);
        return Result.ok(u.getBalance());
    }

    // 注: 早期"全局装配 /api/skins/equip" 已废弃删除 —— 明日方舟模式下
    // 皮肤按干员独立装配, 统一走 /api/skins/equip-operator, 不存在全员共享一款皮肤的情况。
}
