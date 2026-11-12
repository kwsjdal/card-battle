package com.cardbattle.battle;

import com.cardbattle.common.BizException;
import com.cardbattle.dto.BattleAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

/**
 * 战斗操作入口(STOMP): 前端 send("/app/battle.action", action)
 *
 * 【安全】操作者身份一律取自 WS 握手时写入会话的 uid(由 JWT 解析),
 *         不使用报文里的 action.userId —— 否则任意登录账号都能伪造他人身份出牌/认输。
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class BattleWsController {

    private final BattleManager battleManager;

    @MessageMapping("/battle.action")
    public void onAction(@Payload BattleAction action, SimpMessageHeaderAccessor accessor) {
        Long uid = uidOf(accessor);
        if (uid == null) throw new BizException("未登录或登录已过期, 请重新登录");

        BattleRoom room = battleManager.get(action.getRoomId());
        if (room == null) throw new BizException("房间不存在或已结束");
        // 房间归属校验: 只有房间内的真人玩家才能操作该房间
        if (!room.hasPlayer(uid)) throw new BizException("无权操作该房间");

        switch (action.getType() == null ? "" : action.getType()) {
            case "play":      room.playCard(uid, nz(action.getHandIndex())); break;
            case "attack":    room.attack(uid, nz(action.getBoardIndex()), nz(action.getTargetIndex())); break;
            case "hero":      room.heroPower(uid, nz(action.getTargetIndex())); break;
            case "end":       room.endTurn(uid); break;
            case "surrender": room.surrender(uid); break;
            default: throw new BizException("未知操作类型");
        }

        if (room.getPhase() == BattleRoom.Phase.GAME_OVER) {
            battleManager.sendGameOver(room);
            battleManager.finish(room, room.getWinnerId());
        } else {
            battleManager.broadcast(room);
            battleManager.maybeBotTurn(room);   // 轮到机器人时触发AI行动
        }
    }

    /** 从会话属性取握手时写入的 uid(唯一可信来源) */
    private Long uidOf(SimpMessageHeaderAccessor accessor) {
        Object attrs = accessor == null ? null : accessor.getSessionAttributes();
        if (!(attrs instanceof java.util.Map)) return null;
        Object uid = ((java.util.Map<String, Object>) attrs).get("uid");
        if (uid == null) return null;
        try {
            return Long.valueOf(uid.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int nz(Integer i) { return i == null ? -999 : i; }

    /**
     * 战斗操作异常回执。
     * Spring 默认不把 @MessageMapping 抛出的异常发回客户端(只在服务端打日志)，
     * 玩家点了法力不足的牌、或被「嘲讽」拦下时界面将毫无反应。
     * 这里统一转成私密 error 帧推给操作者本人，前端据此弹出提示。
     */
    @org.springframework.messaging.handler.annotation.MessageExceptionHandler
    public void onError(Exception e, SimpMessageHeaderAccessor accessor) {
        Long uid = uidOf(accessor);
        String msg = (e instanceof BizException) ? e.getMessage() : "操作失败，请重试";
        battleManager.sendError(uid, msg);
    }
}
