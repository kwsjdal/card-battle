package com.cardbattle.controller;

import com.cardbattle.battle.BattleManager;
import com.cardbattle.battle.BattleRoom;
import com.cardbattle.common.Result;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/** 战斗 HTTP 接口（快照拉取 + 手动加入房间） */
@RestController
@RequestMapping("/api/battle")
@RequiredArgsConstructor
public class BattleController {

    private final BattleManager battleManager;

    /**
     * 前端进入 battle 页面时主动拉取当前状态快照（解决 STOMP 竞态）
     *
     * 【安全】viewerId 一律取自 JWT（JwtInterceptor 写入的 request 属性），不再信任 URL 参数,
     *         且必须校验该用户确实在本房间内 —— 早期版本传任意 userId 就能读到对手手牌。
     */
    @GetMapping("/{roomId}/snapshot")
    public Result<Map<String, Object>> snapshot(@PathVariable String roomId, HttpServletRequest req) {
        Long viewerId = (Long) req.getAttribute("userId");
        if (viewerId == null) return Result.fail("未登录或登录已过期");
        BattleRoom room = battleManager.get(roomId);
        if (room == null) return Result.fail("房间不存在或已结束");
        if (!room.hasPlayer(viewerId)) return Result.fail("无权查看该对局");
        return Result.ok(room.snapshotFor(viewerId));
    }

    /** 进行中的对局列表(观战大厅) */
    @GetMapping("/live")
    public Result<List<Map<String, Object>>> live() {
        return Result.ok(battleManager.liveRooms());
    }

    /**
     * 进入观战: 返回一次脱敏快照。之后持续更新走 WS 频道 /topic/battle/{roomId}。
     * 观战不需要是房间参与者, 但快照严格脱敏(双方手牌均不下发)。
     */
    @GetMapping("/{roomId}/spectate")
    public Result<Map<String, Object>> spectate(@PathVariable String roomId, HttpServletRequest req) {
        if (req.getAttribute("userId") == null) return Result.fail("未登录或登录已过期");
        BattleRoom room = battleManager.get(roomId);
        if (room == null) return Result.fail("对局已结束或不存在");
        return Result.ok(room.spectatorView());
    }
}
