package com.cardbattle.controller;

import com.cardbattle.common.Result;
import com.cardbattle.service.BonusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/** 每日福利: 签到 + 任务(奖励水晶) */
@RestController
@RequestMapping("/api/bonus")
@RequiredArgsConstructor
public class BonusController {

    private final BonusService bonusService;

    /** 福利总览: 签到状态/连续天数/任务进度/水晶余额 */
    @GetMapping("/info")
    public Result<Map<String, Object>> info(HttpServletRequest req) {
        return Result.ok(bonusService.info((Long) req.getAttribute("userId")));
    }

    /** 每日签到 */
    @PostMapping("/sign")
    public Result<Map<String, Object>> sign(HttpServletRequest req) {
        return Result.ok(bonusService.sign((Long) req.getAttribute("userId")));
    }

    /** 领取任务奖励: {key: "battle_1" | "win_1" | "pull_1"} */
    @PostMapping("/tasks/claim")
    public Result<Map<String, Object>> claim(HttpServletRequest req, @RequestBody Map<String, String> body) {
        return Result.ok(bonusService.claim((Long) req.getAttribute("userId"), body.get("key")));
    }
}
