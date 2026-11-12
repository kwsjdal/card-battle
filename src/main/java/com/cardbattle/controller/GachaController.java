package com.cardbattle.controller;

import com.cardbattle.common.Result;
import com.cardbattle.service.GachaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/gacha")
@RequiredArgsConstructor
public class GachaController {

    private final GachaService gachaService;
    private final com.cardbattle.service.IdempotencyService idempotency;

    /** 卡池: 全部卡 + 已拥有标记 */
    @GetMapping("/pool")
    public Result<Map<String, Object>> pool(HttpServletRequest req) {
        return Result.ok(gachaService.pool((Long) req.getAttribute("userId")));
    }

    /**
     * 抽卡: {times: 1 | 10, currency: "gold"(默认) | "crystal", idemKey: "一次性UUID"}
     * idemKey 用于防重复扣费: 网络重试或连点带同一个 key 时回放首次结果, 不二次扣除货币。
     */
    @PostMapping("/pull")
    public Result<Map<String, Object>> pull(HttpServletRequest req, @RequestBody Map<String, Object> body) {
        Object timesObj = body.get("times");
        int times = (timesObj instanceof Number) ? ((Number) timesObj).intValue() : 1;
        Object cur = body.get("currency");
        String currency = cur == null ? "gold" : cur.toString();
        Long uid = (Long) req.getAttribute("userId");
        Object idem = body.get("idemKey");
        return Result.ok(idempotency.run(uid, idem == null ? null : idem.toString(),
                () -> gachaService.pull(uid, times, currency)));
    }
}
