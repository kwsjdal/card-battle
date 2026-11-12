package com.cardbattle.service;

import com.cardbattle.common.BizException;
import com.cardbattle.entity.User;
import com.cardbattle.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 每日福利: 签到 + 任务
 * 水晶只能从签到/任务获得, 用于抽卡(比金币贵 100/抽)
 * 进度与领取状态全部存 Redis, 按天过期, 无需新建 MySQL 表
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BonusService {

    /** 签到基础奖励(水晶) */
    public static final int SIGN_BASE = 50;
    /** 连续签到每天额外 +10, 封顶第 7 天 */
    public static final int SIGN_STREAK_BONUS = 10;

    /** 每日任务定义: key -> (描述, 目标次数, 奖励水晶) */
    private static final Map<String, TaskDef> TASKS = new LinkedHashMap<>();
    static {
        TASKS.put("battle_1", new TaskDef("完成 1 场对局", 1, 30));
        TASKS.put("win_1",    new TaskDef("取得 1 场胜利", 1, 50));
        TASKS.put("pull_1",   new TaskDef("抽卡 1 次", 1, 20));
    }

    private static final class TaskDef {
        final String desc;
        final int target;
        final int reward;
        TaskDef(String desc, int target, int reward) { this.desc = desc; this.target = target; this.reward = reward; }
    }

    private final StringRedisTemplate redis;
    private final UserMapper userMapper;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private String today() { return LocalDate.now().format(DAY); }
    private String cntKey(Long uid, String day, String taskKey) { return "task:cnt:" + uid + ":" + day + ":" + taskKey; }
    private String claimedKey(Long uid, String day, String taskKey) { return "task:claimed:" + uid + ":" + day + ":" + taskKey; }

    /** 对局结算时累计任务进度(双方 +battle_1, 胜方 +win_1) */
    public void onBattleEnd(Long player1, Long player2, Long winnerId) {
        String day = today();
        incrTask(player1, day, "battle_1", 1);
        incrTask(player2, day, "battle_1", 1);
        if (winnerId != null) incrTask(winnerId, day, "win_1", 1);
    }

    /** 抽卡时累计任务进度 */
    public void onPull(Long userId, int times) {
        incrTask(userId, today(), "pull_1", times);
    }

    private void incrTask(Long userId, String day, String taskKey, int delta) {
        if (userId == null || !TASKS.containsKey(taskKey)) return;
        try {
            redis.opsForValue().increment(cntKey(userId, day, taskKey), delta);
            // 当天键 48h 后自动过期, 免清理
            redis.expire(cntKey(userId, day, taskKey), Duration.ofHours(48));
        } catch (Exception e) {
            log.warn("任务进度写入失败: uid={} task={}", userId, taskKey, e);
        }
    }

    /** 福利页总览: 签到状态 + 连续天数 + 任务进度列表 + 水晶余额 */
    public Map<String, Object> info(Long userId) {
        String day = today();
        User u = userMapper.selectById(userId);
        Boolean signed = redis.hasKey("bonus:sign:" + userId + ":" + day);
        String streakStr = redis.opsForValue().get("bonus:streak:" + userId);
        int streak = streakStr == null ? 0 : Integer.parseInt(streakStr);

        List<Map<String, Object>> tasks = new ArrayList<>();
        for (var e : TASKS.entrySet()) {
            String cnt = redis.opsForValue().get(cntKey(userId, day, e.getKey()));
            Boolean claimed = redis.hasKey(claimedKey(userId, day, e.getKey()));
            Map<String, Object> m = new HashMap<>();
            m.put("key", e.getKey());
            m.put("desc", e.getValue().desc);
            m.put("target", e.getValue().target);
            m.put("progress", cnt == null ? 0 : Math.min(Integer.parseInt(cnt), e.getValue().target));
            m.put("reward", e.getValue().reward);
            m.put("claimed", Boolean.TRUE.equals(claimed));
            tasks.add(m);
        }

        Map<String, Object> r = new HashMap<>();
        r.put("crystal", u == null || u.getCrystal() == null ? 0 : u.getCrystal());
        r.put("signedToday", Boolean.TRUE.equals(signed));
        r.put("streak", streak);
        r.put("signReward", SIGN_BASE + Math.min(streak, 7) * SIGN_STREAK_BONUS);
        r.put("tasks", tasks);
        return r;
    }

    /** 每日签到: 一天一次, 连续签到有额外水晶 */
    public Map<String, Object> sign(Long userId) {
        String day = today();
        String signKey = "bonus:sign:" + userId + ":" + day;
        Boolean first = redis.opsForValue().setIfAbsent(signKey, "1", Duration.ofHours(48));
        if (first == null || !first) throw new BizException("今天已经签到过啦, 明天再来");

        // 连续天数: 昨天签过则 +1, 否则重新计 1
        String streakKey = "bonus:streak:" + userId;
        long streak;
        String yesterday = LocalDate.now().minusDays(1).format(DAY);
        String last = redis.opsForValue().get("bonus:lastsign:" + userId);
        if (yesterday.equals(last)) {
            streak = redis.opsForValue().increment(streakKey, 1);
        } else {
            streak = 1;
            redis.opsForValue().set(streakKey, "1");
        }
        redis.opsForValue().set("bonus:lastsign:" + userId, day);

        int reward = SIGN_BASE + (int) Math.min(streak, 7) * SIGN_STREAK_BONUS;
        addCrystal(userId, reward);
        log.info("签到: uid={} streak={} reward={}", userId, streak, reward);

        Map<String, Object> r = new HashMap<>();
        r.put("reward", reward);
        r.put("streak", streak);
        r.put("crystal", crystalOf(userId));
        return r;
    }

    /** 领取任务奖励 */
    public Map<String, Object> claim(Long userId, String taskKey) {
        TaskDef def = TASKS.get(taskKey);
        if (def == null) throw new BizException("任务不存在");
        String day = today();
        String cnt = redis.opsForValue().get(cntKey(userId, day, taskKey));
        int progress = cnt == null ? 0 : Integer.parseInt(cnt);
        if (progress < def.target) throw new BizException("任务尚未完成");

        Boolean first = redis.opsForValue().setIfAbsent(claimedKey(userId, day, taskKey), "1", Duration.ofHours(48));
        if (first == null || !first) throw new BizException("奖励已领取");

        addCrystal(userId, def.reward);
        Map<String, Object> r = new HashMap<>();
        r.put("reward", def.reward);
        r.put("crystal", crystalOf(userId));
        return r;
    }

    private void addCrystal(Long userId, int amount) {
        User u = userMapper.selectById(userId);
        if (u == null) throw new BizException("用户不存在");
        u.setCrystal((u.getCrystal() == null ? 0 : u.getCrystal()) + amount);
        userMapper.updateById(u);
    }

    private int crystalOf(Long userId) {
        User u = userMapper.selectById(userId);
        return u == null || u.getCrystal() == null ? 0 : u.getCrystal();
    }
}
