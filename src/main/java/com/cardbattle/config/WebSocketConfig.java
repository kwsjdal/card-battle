package com.cardbattle.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * WebSocket(STOMP)配置
 * 前端连接: new Client({brokerURL: "ws://localhost:8080/ws?token=<jwt>"})
 * 订阅通道: /topic/user/{userId}      匹配结果通知 / 战斗实况(按人定制)
 *           /topic/battle/{roomId}    观战只读频道(脱敏快照)
 * 发送目标: /app/battle.action
 *
 * 【安全】握手时必须携带 JWT。uid 一律由 token 解析得出并写入会话属性,
 *         不再信任 URL 上的 uid 参数, 也不再信任前端报文里的 userId
 *         —— 早期版本正是因此可以被任意登录账号伪造他人身份出牌/认输。
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * 延迟解析: AuthSupport -> SessionService -> SimpMessagingTemplate -> WebSocketConfig 构成依赖环
     * (SessionService 要用消息模板推送"被挤下线")。握手发生在运行时, 那时 Bean 都已就绪,
     * 因此用 ObjectProvider 延迟获取即可打破启动期的循环依赖。
     */
    private final org.springframework.beans.factory.ObjectProvider<AuthSupport> authProvider;

    public WebSocketConfig(org.springframework.beans.factory.ObjectProvider<AuthSupport> authProvider) {
        this.authProvider = authProvider;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*")
                .addInterceptors(new HandshakeInterceptor() {
                    /**
                     * 握手鉴权: 解析 ?token=<jwt>, 校验通过后把 userId 写入会话属性。
                     * 校验规则与 HTTP 接口完全一致(单设备登录互踢 / 封禁), 见 AuthSupport。
                     * 返回 false 则直接拒绝握手(后端不会为该连接创建任何会话)。
                     */
                    @Override
                    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                                   WebSocketHandler wsHandler, java.util.Map<String, Object> attributes) {
                        String token = queryParam(request.getURI().getQuery(), "token");
                        AuthSupport.AuthResult r = new AuthSupport.AuthResult();
                        Long uid = authProvider.getObject().authenticate(token, r);
                        if (uid == null) {
                            // 注意: 不要把 query 整体打进日志, 里面含 token
                            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                            return false;
                        }
                        attributes.put("uid", uid);           // 唯一可信来源
                        attributes.put("authMsg", r.msg);
                        return true;
                    }

                    @Override
                    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                               WebSocketHandler wsHandler, Exception exception) { }
                });
    }

    /** 从 query 串里取参数(只用于解析 token, 不做其它用途) */
    private static String queryParam(String query, String key) {
        if (query == null || query.isEmpty()) return null;
        String prefix = key + "=";
        for (String part : query.split("&")) {
            if (part.startsWith(prefix)) {
                return URLDecoder.decode(part.substring(prefix.length()), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
