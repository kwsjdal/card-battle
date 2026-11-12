package com.cardbattle.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 单设备登录(互踢)服务
 *   Redis: login:token:{userId} = 当前唯一有效的 token
 *   - 新设备登录 -> 覆盖旧 token, 并通过 WebSocket 给旧设备推 kicked 事件
 *   - 旧设备再发任何请求 -> JwtInterceptor 校验不通过 -> 401 + 提示"账号已在其他设备登录"
 * 论文可写点: 会话唯一性 + 主动下线推送(而不是等下一个请求才被动失效)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final String KEY = "login:token:";
    private static final long TTL_DAYS = 7;

    private final RedisTemplate<String, Object> redis;
    private final SimpMessagingTemplate ws;

    /** 登录成功: 登记本设备 token, 旧设备若有不同 token 则被挤下线 */
    public void login(Long userId, String token) {
        String k = KEY + userId;
        Object old = redis.opsForValue().get(k);
        if (old != null && !token.equals(String.valueOf(old))) {
            Map<String, Object> ev = new HashMap<>();
            ev.put("event", "kicked");
            ev.put("msg", "你的账号在另一台设备登录，当前设备已退出");
            try {
                ws.convertAndSend("/topic/user/" + userId, ev);
            } catch (Exception e) {
                log.warn("下线推送失败(旧设备可能未连接WS): {}", e.getMessage());
            }
            log.info("账号[{}]在新设备登录, 旧设备已下线", userId);
        }
        redis.opsForValue().set(k, token, TTL_DAYS, TimeUnit.DAYS);
    }

    /** 当前 token 是否是该账号的有效会话 */
    public boolean valid(Long userId, String token) {
        String k = KEY + userId;
        Object cur = redis.opsForValue().get(k);
        if (cur == null) {
            // Redis 丢数据时的自愈: 直接把当前 token 认定为有效, 避免误伤在线玩家
            redis.opsForValue().set(k, token, TTL_DAYS, TimeUnit.DAYS);
            return true;
        }
        return token.equals(String.valueOf(cur));
    }

    /** 主动退出: 清除会话标记 */
    public void logout(Long userId) {
        redis.delete(KEY + userId);
    }
}
