package com.cardbattle.controller;

import com.cardbattle.common.Result;
import com.cardbattle.service.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    /** 进入匹配队列 */
    @PostMapping("/join")
    public Result<Void> join(HttpServletRequest req) {
        matchService.join((Long) req.getAttribute("userId"));
        return Result.ok();
    }

    /** 取消匹配 */
    @PostMapping("/leave")
    public Result<Void> leave(HttpServletRequest req) {
        matchService.leave((Long) req.getAttribute("userId"));
        return Result.ok();
    }

    /** 匹配状态: 队列人数 + 我已等待秒数(前端用来提示"还在等人") */
    @GetMapping("/status")
    public Result<java.util.Map<String, Object>> status(HttpServletRequest req) {
        return Result.ok(matchService.status((Long) req.getAttribute("userId")));
    }

    /**
     * 人机对战: 立即开局, 单账号可演示完整对战。
     * @param level 机器人难度 easy / normal / hard(缺省 normal)
     */
    @PostMapping("/bot")
    public Result<String> joinBot(HttpServletRequest req,
                                  @RequestParam(required = false) String level) {
        return Result.ok(matchService.joinBot((Long) req.getAttribute("userId"), level));
    }
}
