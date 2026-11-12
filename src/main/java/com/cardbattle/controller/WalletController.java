package com.cardbattle.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cardbattle.common.BizException;
import com.cardbattle.common.Result;
import com.cardbattle.entity.RechargeLog;
import com.cardbattle.entity.User;
import com.cardbattle.mapper.RechargeLogMapper;
import com.cardbattle.mapper.UserMapper;
import com.cardbattle.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 模拟钱包: 充值/余额/流水 (演示用, 无真实支付) */
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final UserMapper userMapper;
    private final RechargeLogMapper rechargeLogMapper;
    private final IdempotencyService idempotency;

    /** 模拟充值: 直接加余额并落流水。body 可带 idemKey 防重复提交(见 IdempotencyService) */
    @PostMapping("/recharge")
    @Transactional
    public Result<Map<String, Object>> recharge(HttpServletRequest req, @RequestBody Map<String, Object> body) {
        Long userId = (Long) req.getAttribute("userId");
        int amount = body.get("amount") instanceof Number ? ((Number) body.get("amount")).intValue() : 0;
        Object idem = body.get("idemKey");
        return Result.ok(idempotency.run(userId, idem == null ? null : idem.toString(), () -> {
            java.util.Map<String, Object> r = new java.util.LinkedHashMap<>();
            r.put("balance", doRecharge(userId, amount));
            return r;
        }));
    }

    /** 真正的充值逻辑(由幂等执行器包裹) */
    private int doRecharge(Long userId, int amount) {
        if (amount < 1 || amount > 100000) throw new BizException("充值金额不合法(1~100000)");

        User u = userMapper.selectById(userId);
        if (u == null) throw new BizException("用户不存在");
        u.setBalance(u.getBalance() + amount);
        userMapper.updateById(u);

        RechargeLog log = new RechargeLog();
        log.setUserId(userId);
        log.setAmount(amount);
        log.setBalanceAfter(u.getBalance());
        log.setRemark("模拟支付渠道-" + (amount >= 328 ? "大额礼包" : "金币充值"));
        rechargeLogMapper.insert(log);
        return u.getBalance();
    }

    /** 我的充值流水(分页查询) */
    @GetMapping("/logs")
    public Result<Map<String, Object>> logs(HttpServletRequest req,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        Long userId = (Long) req.getAttribute("userId");
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 10;
        int offset = (page - 1) * size;

        LambdaQueryWrapper<RechargeLog> w = new LambdaQueryWrapper<RechargeLog>()
                .eq(RechargeLog::getUserId, userId);
        long total = rechargeLogMapper.selectCount(w);
        List<RechargeLog> list = rechargeLogMapper.selectList(
                new LambdaQueryWrapper<RechargeLog>()
                        .eq(RechargeLog::getUserId, userId)
                        .orderByDesc(RechargeLog::getId)
                        .last("limit " + offset + "," + size));

        Map<String, Object> r = new HashMap<>();
        r.put("list", list);
        r.put("total", total);
        r.put("page", page);
        r.put("size", size);
        return Result.ok(r);
    }

    /** 金币兑换水晶: 汇率 2金币=1水晶(水晶抽卡贵100, 价值约为金币2倍) */
    @PostMapping("/exchange")
    @Transactional
    public Result<Map<String, Integer>> exchange(HttpServletRequest req, @RequestBody Map<String, Integer> body) {
        Long userId = (Long) req.getAttribute("userId");
        int gold = body.getOrDefault("gold", 0);
        if (gold < 100 || gold % 100 != 0) throw new BizException("兑换数量需为 100 的整数倍");
        if (gold > 100000) throw new BizException("单次兑换上限 100000 金币");

        User u = userMapper.selectById(userId);
        if (u == null) throw new BizException("用户不存在");
        if (u.getBalance() < gold) throw new BizException("金币不足");
        int crystal = gold / 2;

        u.setBalance(u.getBalance() - gold);
        u.setCrystal((u.getCrystal() == null ? 0 : u.getCrystal()) + crystal);
        userMapper.updateById(u);

        RechargeLog log = new RechargeLog();
        log.setUserId(userId);
        log.setAmount(0);
        log.setBalanceAfter(u.getBalance());
        log.setRemark("金币兑换水晶 -" + gold + "金币 +" + crystal + "水晶");
        rechargeLogMapper.insert(log);

        Map<String, Integer> r = new HashMap<>();
        r.put("balance", u.getBalance());
        r.put("crystal", u.getCrystal());
        r.put("crystalGot", crystal);
        return Result.ok(r);
    }

    /** 我的余额 + 最近流水 */
    @GetMapping("/me")
    public Result<Map<String, Object>> mine(HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        User u = userMapper.selectById(userId);
        List<RechargeLog> logs = rechargeLogMapper.selectList(new LambdaQueryWrapper<RechargeLog>()
                .eq(RechargeLog::getUserId, userId).orderByDesc(RechargeLog::getId).last("LIMIT 20"));
        Map<String, Object> r = new HashMap<>();
        r.put("balance", u == null ? 0 : u.getBalance());
        r.put("crystal", u == null || u.getCrystal() == null ? 0 : u.getCrystal());
        r.put("role", u == null ? "user" : u.getRole());
        r.put("logs", logs);
        return Result.ok(r);
    }
}
