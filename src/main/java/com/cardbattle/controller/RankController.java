package com.cardbattle.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cardbattle.common.Result;
import com.cardbattle.entity.User;
import com.cardbattle.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 天梯排行榜 —— 开题关键技术点③: Redis ZSet 实现实时排行
 * 设计: rank:zset 存 userId→rating, 每次结算时 zAdd 更新
 * 查询: reverseRangeWithScores 单次取回 id+分数, 批量 selectById 补昵称/胜负场
 * 兜底: ZSet 为空时从 DB 重建全量(pipeline 批量写入)
 */
@Slf4j
@RestController
@RequestMapping("/api/rank")
@RequiredArgsConstructor
public class RankController {

    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String RANK_ZSET_KEY = "rank:zset";
    private static final int TOP_N = 50;

    /** 天梯排行榜Top50 —— Redis ZSet O(logN), 单次往返取回 id+分数 */
    @GetMapping("/top")
    public Result<List<User>> top() {
        ZSetOperations<String, Object> zset = redisTemplate.opsForZSet();
        Long total = zset.zCard(RANK_ZSET_KEY);

        if (total == null || total == 0) {
            rebuildFromDb();
            total = zset.zCard(RANK_ZSET_KEY);
        }

        if (total == null || total == 0) {
            return Result.ok(List.of());
        }

        Set<ZSetOperations.TypedTuple<Object>> topTuples =
                zset.reverseRangeWithScores(RANK_ZSET_KEY, 0, Math.min(TOP_N - 1, total - 1));
        if (topTuples == null || topTuples.isEmpty()) {
            return Result.ok(List.of());
        }

        // 批量查用户补全昵称 + 排序保持 ZSet 顺序(分数已在 tuple 内, 无需逐个 score())
        List<Long> idList = new ArrayList<>();
        Map<Long, Double> scoreMap = new HashMap<>();
        for (ZSetOperations.TypedTuple<Object> t : topTuples) {
            if (t.getValue() == null) continue;
            Long id = Long.valueOf(t.getValue().toString());
            idList.add(id);
            scoreMap.put(id, t.getScore());
        }
        Map<Long, User> map = userMapper.selectBatchIds(idList).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        List<User> result = new ArrayList<>();
        for (Long id : idList) {
            User u = map.get(id);
            if (u != null) {
                Double score = scoreMap.get(id);
                if (score != null) u.setRating(score.intValue());
                result.add(u);
            }
        }
        return Result.ok(result);
    }

    /** 从 DB 全量重建 ZSet(空时兜底, pipeline 批量写入减少往返) */
    private void rebuildFromDb() {
        List<User> all = userMapper.selectList(
                new LambdaQueryWrapper<User>().select(User::getId, User::getRating));
        if (all == null || all.isEmpty()) return;
        RedisSerializer<String> keySer = redisTemplate.getStringSerializer();
        RedisSerializer<Object> valSer = (RedisSerializer<Object>) redisTemplate.getValueSerializer();
        redisTemplate.executePipelined((RedisCallback<Object>) conn -> {
            byte[] key = keySer.serialize(RANK_ZSET_KEY);
            for (User u : all) {
                conn.zAdd(key, u.getRating(), valSer.serialize(String.valueOf(u.getId())));
            }
            return null;
        });
        log.info("天梯ZSet重建完成, 共 {} 条", all.size());
    }

    /**
     * 查询"我的"天梯排名（前端用于在榜单外显示自己是第几名）。
     * 【安全】userId 一律取自 JWT，不接受调用方传参 —— 否则可以枚举他人排名。
     */
    @GetMapping("/myRank")
    public Result<Map<String, Object>> myRank(javax.servlet.http.HttpServletRequest req) {
        Object attr = req.getAttribute("userId");
        if (attr == null) return Result.fail("未登录或登录已过期");
        Long userId = Long.valueOf(String.valueOf(attr));
        Map<String, Object> r = new HashMap<>();
        ZSetOperations<String, Object> zset = redisTemplate.opsForZSet();
        Long rank = zset.reverseRank(RANK_ZSET_KEY, String.valueOf(userId));
        Double score = zset.score(RANK_ZSET_KEY, String.valueOf(userId));
        r.put("rank", rank == null ? -1 : rank + 1);
        r.put("rating", score == null ? 0 : score.intValue());
        return Result.ok(r);
    }
}
