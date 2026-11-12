package com.cardbattle.config;

import com.cardbattle.entity.User;
import com.cardbattle.mapper.UserMapper;
import com.cardbattle.service.BanMaintenance;
import com.cardbattle.service.SessionService;
import com.cardbattle.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 统一鉴权入口: HTTP 拦截器(JwtInterceptor) 与 WebSocket 握手(WebSocketConfig) 复用同一套规则。
 *
 * 之所以要抽出来: 早期 /ws 走 STOMP 不经过 MVC 拦截器, 导致握手完全不校验身份,
 * 战斗操作的 userId 只能由前端报文携带 —— 任意登录账号都能伪造他人身份出牌/认输。
 * 现在两条链路共用本类, 身份一律从 token 解析, 杜绝"逻辑分叉导致一边漏校验"。
 */
@Component
@RequiredArgsConstructor
public class AuthSupport {

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final SessionService sessionService;

    /**
     * 校验 token 并返回 userId。
     *
     * @return 通过时返回 userId; 失败时返回 null, 并往 result 里写入失败原因
     */
    public Long authenticate(String token, AuthResult result) {
        if (token == null || token.isEmpty()) return fail(result, "未登录或登录已过期");
        Long userId = jwtUtil.parse(token);
        if (userId == null) return fail(result, "未登录或登录已过期");

        // 单设备登录: 该 token 不是本账号当前有效会话 -> 已被其它设备挤下线
        if (!sessionService.valid(userId, token)) return fail(result, "账号已在其他设备登录，当前设备已退出");

        User u = userMapper.selectById(userId);
        if (u == null) return fail(result, "账号不存在");

        // 时限封禁: 到期自动解封放行
        if (u.getStatus() != null && u.getStatus() == 1) {
            if (u.getBannedUntil() != null && u.getBannedUntil().isBefore(java.time.LocalDateTime.now())) {
                BanMaintenance.unbanExpired(userMapper, u);
            } else {
                String until = u.getBannedUntil() == null ? "" : ", 解封时间:" + u.getBannedUntil().toString().replace('T', ' ');
                return fail(result, "账号已被封禁" + until);
            }
        }
        return userId;
    }

    private Long fail(AuthResult result, String msg) {
        if (result != null) result.msg = msg;
        return null;
    }

    /** 承载失败原因(避免用异常传递流程) */
    public static class AuthResult {
        public String msg = "未登录或登录已过期";
    }
}
