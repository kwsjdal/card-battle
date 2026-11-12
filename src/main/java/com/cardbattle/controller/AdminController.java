package com.cardbattle.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cardbattle.common.BizException;
import com.cardbattle.common.Result;
import com.cardbattle.entity.MatchRecord;
import com.cardbattle.entity.RechargeLog;
import com.cardbattle.entity.User;
import com.cardbattle.entity.UserCard;
import com.cardbattle.mapper.MatchRecordMapper;
import com.cardbattle.mapper.RechargeLogMapper;
import com.cardbattle.mapper.UserCardMapper;
import com.cardbattle.mapper.UserMapper;
import com.cardbattle.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/** 管理后台: /api/admin/** 已由 AdminInterceptor 鉴权(role=admin) */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserMapper userMapper;
    private final MatchRecordMapper matchRecordMapper;
    private final RechargeLogMapper rechargeLogMapper;
    private final UserCardMapper userCardMapper;
    private final com.cardbattle.mapper.UserDeckMapper userDeckMapper;
    private final com.cardbattle.mapper.UserSkinMapper userSkinMapper;
    private final com.cardbattle.mapper.UserOperatorSkinMapper userOperatorSkinMapper;
    private final com.cardbattle.mapper.GachaLogMapper gachaLogMapper;
    private final RedisTemplate<String, Object> redis;
    private static final String BOT_NICK = "训练机器人·佩佩";

    /** 用户列表: 关键字(账号/昵称/手机号) + 状态筛选 + 分页 */
    @GetMapping("/users")
    public Result<Map<String, Object>> users(@RequestParam(defaultValue = "") String keyword,
                                             @RequestParam(defaultValue = "all") String status,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        String kw = keyword == null ? "" : keyword.trim();
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        if (!kw.isEmpty()) {
            qw.and(w -> w.like(User::getUsername, kw)
                    .or().like(User::getNickname, kw)
                    .or().like(User::getPhone, kw));
        }
        if ("normal".equals(status)) qw.eq(User::getStatus, 0);
        else if ("banned".equals(status)) qw.eq(User::getStatus, 1);

        long total = userMapper.selectCount(qw);
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        List<User> list = userMapper.selectList(qw.orderByDesc(User::getCreateTime)
                .last("LIMIT " + s + " OFFSET " + (p - 1) * s));

        Map<String, Object> r = new HashMap<>();
        r.put("list", list);
        r.put("total", total);
        return Result.ok(r);
    }

    /** 用户详情: 基础信息 + 卡牌解锁数 + 最近10场对局 + 最近充值 */
    @GetMapping("/users/{id}/detail")
    public Result<Map<String, Object>> userDetail(@PathVariable Long id) {
        User u = userMapper.selectById(id);
        if (u == null) throw new BizException("用户不存在");

        List<MatchRecord> recent = matchRecordMapper.selectList(new LambdaQueryWrapper<MatchRecord>()
                .and(w -> w.eq(MatchRecord::getPlayer1Id, id).or().eq(MatchRecord::getPlayer2Id, id))
                .orderByDesc(MatchRecord::getId).last("LIMIT 10"));

        List<RechargeLog> logs = rechargeLogMapper.selectList(new LambdaQueryWrapper<RechargeLog>()
                .eq(RechargeLog::getUserId, id)
                .orderByDesc(RechargeLog::getId).last("LIMIT 10"));

        Set<Long> uids = new HashSet<>();
        recent.forEach(x -> { uids.add(x.getPlayer1Id()); uids.add(x.getPlayer2Id()); });

        Map<String, Object> r = new HashMap<>();
        r.put("user", u);
        r.put("cardCount", userCardMapper.selectCount(new LambdaQueryWrapper<UserCard>()
                .eq(UserCard::getUserId, id)));
        r.put("records", recent);
        r.put("nameMap", nameMap(uids));
        r.put("recharges", logs);
        return Result.ok(r);
    }

    /** 对局记录分页 + 双方昵称映射 */
    @GetMapping("/records")
    public Result<Map<String, Object>> records(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        long total = matchRecordMapper.selectCount(null);
        List<MatchRecord> records = matchRecordMapper.selectList(new LambdaQueryWrapper<MatchRecord>()
                .orderByDesc(MatchRecord::getId).last("LIMIT " + s + " OFFSET " + (p - 1) * s));

        Set<Long> uids = new HashSet<>();
        records.forEach(r -> { uids.add(r.getPlayer1Id()); uids.add(r.getPlayer2Id()); });

        Map<String, Object> r = new HashMap<>();
        r.put("records", records);
        r.put("nameMap", nameMap(uids));
        r.put("total", total);
        return Result.ok(r);
    }

    /** 充值流水分页 */
    @GetMapping("/recharges")
    public Result<Map<String, Object>> recharges(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        long total = rechargeLogMapper.selectCount(null);
        List<RechargeLog> logs = rechargeLogMapper.selectList(new LambdaQueryWrapper<RechargeLog>()
                .orderByDesc(RechargeLog::getId).last("LIMIT " + s + " OFFSET " + (p - 1) * s));

        Set<Long> uids = logs.stream().map(RechargeLog::getUserId).collect(Collectors.toSet());
        Map<Long, String> nameMap = uids.isEmpty() ? new HashMap<>() : userMapper.selectBatchIds(
                        uids.stream().filter(id -> id > 0).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(User::getId, User::getNickname, (a, b) -> a));

        List<Map<String, Object>> list = logs.stream().map(l -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", l.getId());
            m.put("nickname", nameMap.getOrDefault(l.getUserId(), "用户" + l.getUserId()));
            m.put("amount", l.getAmount());
            m.put("balanceAfter", l.getBalanceAfter());
            m.put("remark", l.getRemark());
            m.put("createTime", l.getCreateTime());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> r = new HashMap<>();
        r.put("list", list);
        r.put("total", total);
        return Result.ok(r);
    }

    /** 封禁账号(原因 + 时限, 到期自动解封; 禁止封自己/其他管理员) */
    @PostMapping("/ban")
    @Transactional
    public Result<Void> ban(HttpServletRequest req, @RequestBody Map<String, Object> body) {
        Long selfId = (Long) req.getAttribute("userId");
        Long targetId = body.get("id") == null ? null : Long.valueOf(body.get("id").toString());
        int days = body.get("days") == null ? 3 : Integer.parseInt(body.get("days").toString());
        String reason = body.get("reason") == null ? "" : body.get("reason").toString().trim();
        User u = targetId == null ? null : userMapper.selectById(targetId);
        if (u == null) throw new BizException("用户不存在");
        if (targetId.equals(selfId)) throw new BizException("不能封禁自己");
        if ("admin".equals(u.getRole())) throw new BizException("不能封禁管理员");
        u.setStatus(1);
        u.setBannedUntil(LocalDateTime.now().plusDays(days));
        u.setBanReason(reason.isEmpty() ? "未填写原因" : reason);
        userMapper.updateById(u);
        return Result.ok();
    }

    /** 解封账号(显式清空 banned_until / ban_reason: updateById 忽略 null 字段) */
    @PostMapping("/unban")
    @Transactional
    public Result<Void> unban(@RequestBody Map<String, Long> body) {
        Long targetId = body.get("id");
        User u = targetId == null ? null : userMapper.selectById(targetId);
        if (u == null) throw new BizException("用户不存在");
        userMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>()
                .eq(User::getId, u.getId())
                .set(User::getStatus, 0)
                .set(User::getBannedUntil, null)
                .set(User::getBanReason, null));
        return Result.ok();
    }

    /** 调整玩家金币余额 */
    @PostMapping("/coins")
    @Transactional
    public Result<Void> coins(@RequestBody Map<String, Object> body) {
        Long targetId = body.get("id") == null ? null : Long.valueOf(body.get("id").toString());
        Integer amount = body.get("amount") == null ? null : Integer.valueOf(body.get("amount").toString());
        User u = targetId == null ? null : userMapper.selectById(targetId);
        if (u == null) throw new BizException("用户不存在");
        if (amount == null || amount < 0) throw new BizException("金币数量非法");
        u.setBalance(amount);
        userMapper.updateById(u);
        return Result.ok();
    }

    /** 调整玩家天梯分(DB + Redis 天梯榜同步) */
    @PostMapping("/rating")
    @Transactional
    public Result<Void> rating(@RequestBody Map<String, Object> body) {
        Long targetId = body.get("id") == null ? null : Long.valueOf(body.get("id").toString());
        Integer rating = body.get("rating") == null ? null : Integer.valueOf(body.get("rating").toString());
        User u = targetId == null ? null : userMapper.selectById(targetId);
        if (u == null) throw new BizException("用户不存在");
        if (rating == null || rating < 0) throw new BizException("天梯分非法");
        u.setRating(rating);
        userMapper.updateById(u);
        redis.opsForZSet().add("rank:zset", targetId.toString(), rating);
        return Result.ok();
    }

    /** 重置玩家密码: 生成 8 位随机密码, 哈希存储, 返回明文给管理员转告玩家(参考游戏 GM 工具) */
    @PostMapping("/reset-password")
    @Transactional
    public Result<Map<String, String>> resetPassword(@RequestBody Map<String, Object> body) throws Exception {
        Long targetId = body.get("id") == null ? null : Long.valueOf(body.get("id").toString());
        User u = targetId == null ? null : userMapper.selectById(targetId);
        if (u == null) throw new BizException("用户不存在");
        if ("admin".equals(u.getRole())) throw new BizException("不能重置管理员密码");

        // 8 位随机密码: 大写字母 + 小写字母 + 数字
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        Random rnd = new Random();
        StringBuilder pwd = new StringBuilder();
        for (int i = 0; i < 8; i++) pwd.append(chars.charAt(rnd.nextInt(chars.length())));
        String newPwd = pwd.toString();

        // 新随机盐 + SHA-256 存储; 同时更新可逆密文, 管理员之后也能查看
        String salt = JwtUtil.generateSalt();
        u.setSalt(salt);
        u.setPassword(JwtUtil.hashPassword(newPwd, salt));
        u.setPwdCipher(com.cardbattle.util.CryptoUtil.encrypt(newPwd));
        userMapper.updateById(u);

        log.info("管理员重置玩家密码, targetId={}, username={}", targetId, u.getUsername());

        Map<String, String> r = new HashMap<>();
        r.put("password", newPwd);
        return Result.ok(r);
    }

    /**
     * 查看玩家密码(明文): 登录校验仍用哈希, 这里解密 users.pwd_cipher 返回。
     * 历史账号没有密文时返回 tip, 提示"重置一次密码后即可查看"。
     */
    @PostMapping("/user-password")
    public Result<Map<String, String>> viewPassword(@RequestBody Map<String, Object> body) {
        Long targetId = body.get("id") == null ? null : Long.valueOf(body.get("id").toString());
        User u = targetId == null ? null : userMapper.selectById(targetId);
        if (u == null) throw new BizException("用户不存在");
        if ("admin".equals(u.getRole())) throw new BizException("不能查看管理员密码");

        String plain = com.cardbattle.util.CryptoUtil.decrypt(u.getPwdCipher());
        Map<String, String> r = new HashMap<>();
        if (plain.isEmpty()) {
            r.put("password", "");
            r.put("tip", "该账号为早期注册(仅存哈希), 无法还原; 重置一次密码后即可查看");
        } else {
            r.put("password", plain);
            r.put("tip", "");
        }
        log.info("管理员查看玩家密码, targetId={}, username={}", targetId, u.getUsername());
        return Result.ok(r);
    }

    /**
     * 查看某手机号最近的验证码流水。
     * 演示环境(无真实短信网关)下由管理员查看; 接入短信网关后应删除本接口。
     */
    @GetMapping("/sms-trace")
    public Result<Map<String, Object>> smsTrace(@RequestParam String phone) {
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) throw new BizException("手机号格式不正确");
        List<Object> items = redis.opsForList().range(com.cardbattle.controller.SmsController.SMS_TRACE_KEY + phone, -5, -1);
        Map<String, Object> r = new HashMap<>();
        r.put("phone", com.cardbattle.controller.SmsController.mask(phone));
        r.put("list", items == null ? Collections.emptyList() : items);
        r.put("active", redis.hasKey(com.cardbattle.controller.SmsController.SMS_CODE_KEY + phone));
        return Result.ok(r);
    }

    /**
     * 注销玩家账号(级联删除全部关联数据):
     * 卡牌 / 卡组 / 皮肤 / 干员皮肤装配 / 抽卡记录 / 充值流水 / 对局记录 / 天梯榜 全部清除, 不可恢复。
     * 安全: 不能删自己、不能删管理员, 且必须回填正确账号名二次确认。
     */
    @PostMapping("/delete-user")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> deleteUser(HttpServletRequest req, @RequestBody Map<String, Object> body) {
        Long selfId = (Long) req.getAttribute("userId");
        Long targetId = body.get("id") == null ? null : Long.valueOf(body.get("id").toString());
        String confirm = String.valueOf(body.getOrDefault("username", "")).trim();
        User u = targetId == null ? null : userMapper.selectById(targetId);
        if (u == null) throw new BizException("用户不存在");
        if (targetId.equals(selfId)) throw new BizException("不能删除自己的账号");
        if ("admin".equals(u.getRole())) throw new BizException("不能删除管理员账号");
        if (!u.getUsername().equals(confirm))
            throw new BizException("确认失败: 请输入账号名「" + u.getUsername() + "」后再删除");

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("cards", userCardMapper.delete(new LambdaQueryWrapper<UserCard>().eq(UserCard::getUserId, targetId)));
        r.put("decks", userDeckMapper.delete(new LambdaQueryWrapper<com.cardbattle.entity.UserDeck>().eq(com.cardbattle.entity.UserDeck::getUserId, targetId)));
        r.put("skins", userSkinMapper.delete(new LambdaQueryWrapper<com.cardbattle.entity.UserSkin>().eq(com.cardbattle.entity.UserSkin::getUserId, targetId)));
        r.put("operatorSkins", userOperatorSkinMapper.delete(new LambdaQueryWrapper<com.cardbattle.entity.UserOperatorSkin>().eq(com.cardbattle.entity.UserOperatorSkin::getUserId, targetId)));
        r.put("gachaLogs", gachaLogMapper.delete(new LambdaQueryWrapper<com.cardbattle.entity.GachaLog>().eq(com.cardbattle.entity.GachaLog::getUserId, targetId)));
        r.put("rechargeLogs", rechargeLogMapper.delete(new LambdaQueryWrapper<RechargeLog>().eq(RechargeLog::getUserId, targetId)));
        // 对局记录涉及双方: 删除该玩家参与过的全部对局(对手的历史里也会一并移除)
        r.put("matches", matchRecordMapper.delete(new LambdaQueryWrapper<MatchRecord>()
                .eq(MatchRecord::getPlayer1Id, targetId).or().eq(MatchRecord::getPlayer2Id, targetId)));
        userMapper.deleteById(targetId);

        // Redis 侧: 天梯榜成员 + 签到/任务进度(bonus:streak:N、bonus:lastsign:N、bonus:sign:N:日期、task:cnt:N:...)
        try {
            redis.opsForZSet().remove("rank:zset", targetId.toString());
            redis.delete("bonus:streak:" + targetId);
            redis.delete("bonus:lastsign:" + targetId);
            Set<String> keys = redis.keys("bonus:*:" + targetId + ":*");
            if (keys != null && !keys.isEmpty()) redis.delete(keys);
            Set<String> taskKeys = redis.keys("task:*:" + targetId + ":*");
            if (taskKeys != null && !taskKeys.isEmpty()) redis.delete(taskKeys);
        } catch (Exception ignore) { /* Redis 不可用不影响库里已删干净 */ }

        log.warn("管理员注销账号, targetId={}, username={}, 级联删除={}", targetId, u.getUsername(), r);
        r.put("username", u.getUsername());
        return Result.ok(r);
    }

    /** 平台统计: 注册数/对局数/充值总额(SQL聚合, 不全表加载) */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> r = new HashMap<>();
        r.put("userCount", userMapper.selectCount(null));
        r.put("recordCount", matchRecordMapper.selectCount(null));
        r.put("rechargeTotal", rechargeLogMapper.sumAmount());
        return Result.ok(r);
    }

    /** 近7日趋势: 每日新增用户/对局数/充值金币(SQL GROUP BY 聚合) */
    @GetMapping("/stats/trend")
    public Result<Map<String, Object>> trend() {
        LocalDate start = LocalDate.now().minusDays(6);
        LocalDateTime from = start.atStartOfDay();

        Map<String, Integer> userMap = dayCountMap(userMapper.countByDay(from), "c");
        Map<String, Integer> recordMap = dayCountMap(matchRecordMapper.countByDay(from), "c");
        Map<String, Integer> coinMap = dayCountMap(rechargeLogMapper.sumByDay(from), "s");

        List<String> days = new ArrayList<>();
        List<Integer> newUsers = new ArrayList<>();
        List<Integer> matches = new ArrayList<>();
        List<Integer> coinSum = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        for (int i = 0; i < 7; i++) {
            LocalDate d = start.plusDays(i);
            String key = d.toString();
            days.add(d.format(fmt));
            newUsers.add(userMap.getOrDefault(key, 0));
            matches.add(recordMap.getOrDefault(key, 0));
            coinSum.add(coinMap.getOrDefault(key, 0));
        }

        Map<String, Object> r = new HashMap<>();
        r.put("days", days);
        r.put("newUsers", newUsers);
        r.put("matches", matches);
        r.put("coinSum", coinSum);
        return Result.ok(r);
    }

    /** [{d: 日期, 数值列: 值}] → Map<日期串, 数值> */
    private static Map<String, Integer> dayCountMap(List<Map<String, Object>> rows, String valKey) {
        Map<String, Integer> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object d = row.get("d");
            Object v = row.get(valKey);
            if (d != null && v instanceof Number) map.put(d.toString(), ((Number) v).intValue());
        }
        return map;
    }

    /** 玩家ID → 昵称(机器人固定昵称) */
    private Map<Long, String> nameMap(Set<Long> uids) {
        Map<Long, String> map = uids.isEmpty() ? new HashMap<>() : userMapper.selectBatchIds(
                        uids.stream().filter(id -> id > 0).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(User::getId, User::getNickname, (a, b) -> a));
        map.put(-1L, BOT_NICK);
        return map;
    }
}
