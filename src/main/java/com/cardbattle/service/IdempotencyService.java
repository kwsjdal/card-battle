package com.cardbattle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 幂等执行器: 防止网络重试 / 用户连点导致的重复扣款、重复抽卡。
 *
 * 用法: 前端在提交"消耗型"操作时生成一个一次性 key(如 UUID)随请求带上,
 *       服务端以 idem:{userId}:{key} 为键缓存首次执行结果(TTL 10 分钟)。
 *       重复请求直接回放首次结果, 不再二次扣费, 并在响应里打 idempotent=true 标记。
 *
 * 可靠性点(论文可写): 幂等键 + Redis 缓存首结果 + 结果回放。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final StringRedisTemplate stringRedis;
    private final ObjectMapper om = new ObjectMapper();

    private static final long TTL_MINUTES = 10;

    /**
     * @param userId 当前用户
     * @param key    前端生成的一次性幂等键(为空则不做幂等, 保持向后兼容)
     * @param action 真正的业务逻辑
     */
    public Map<String, Object> run(Long userId, String key, Supplier<Map<String, Object>> action) {
        if (key == null || key.isBlank()) return action.get();

        String rk = "idem:" + userId + ":" + key;
        try {
            String cached = stringRedis.opsForValue().get(rk);
            if (cached != null && !cached.isBlank()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> replay = om.readValue(cached, Map.class);
                replay.put("idempotent", true);     // 告诉前端: 这是重放结果, 未重复扣费
                log.info("幂等命中: userId={}, key={}, 回放首次结果", userId, key);
                return replay;
            }
        } catch (Exception e) {
            // 缓存不可用时降级为不幂等(不阻断业务), 仅记录
            log.warn("幂等缓存读取异常, 降级为普通执行: {}", e.getMessage());
            return action.get();
        }

        Map<String, Object> result = action.get();
        try {
            stringRedis.opsForValue().set(rk, om.writeValueAsString(result), TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("幂等缓存写入异常(不影响本次结果): {}", e.getMessage());
        }
        return result;
    }
}
