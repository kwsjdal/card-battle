package com.cardbattle.battle;

import com.cardbattle.service.MatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * 断线/重连处理
 *  - 匹配中 -> 自动退出队列
 *  - 对局中 -> 不再立刻判负, 而是标记掉线并给 60s 重连宽限期
 *    宽限期内重连则继续对局(手机切后台/弱网抖动不再直接掉分), 超时由
 *    BattleManager.sweep() 统一回收判负
 * 可靠性点(论文可写): 心跳保活 + 多会话计数 + 宽限期 + 定时回收
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsSessionListener {

    private final BattleManager battleManager;
    private final MatchService matchService;

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        Long uid = uidOf(event.getMessage());
        if (uid == null) return;
        battleManager.wsConnect(uid);
        BattleRoom room = battleManager.findByUser(uid);
        if (room != null && room.getPhase() != BattleRoom.Phase.GAME_OVER) {
            if (room.offlineUserIds().contains(uid)) {
                room.markOnline(uid);
                battleManager.broadcast(room);          // 让对手看到"对手已重连"
                log.info("用户[{}]在宽限期内重连, 对局继续", uid);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Long uid = uidOf(event.getMessage());
        if (uid == null) return;
        // 同一账号可能开多个页面, 只有最后一个会话断开才算真掉线
        if (!battleManager.wsDisconnect(uid)) return;

        matchService.leave(uid);                       // 排队中断线: 出队
        BattleRoom room = battleManager.findByUser(uid);
        if (room != null && room.getPhase() != BattleRoom.Phase.GAME_OVER) {
            room.markOffline(uid);
            log.warn("用户[{}]对局中掉线, 进入{}s重连宽限期", uid, BattleRoom.OFFLINE_GRACE_MS / 1000);
            battleManager.broadcast(room);              // 对手侧显示"对手掉线"
        }
    }

    private Long uidOf(Message<?> message) {
        if (message == null) return null;
        Object attrs = message.getHeaders().get(SimpMessageHeaderAccessor.SESSION_ATTRIBUTES);
        if (!(attrs instanceof java.util.Map)) return null;
        Object uidAttr = ((java.util.Map<String, Object>) attrs).get("uid");
        if (uidAttr == null) return null;
        try {
            return Long.valueOf(uidAttr.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
