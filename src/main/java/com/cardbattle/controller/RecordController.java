package com.cardbattle.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cardbattle.common.Result;
import com.cardbattle.entity.MatchRecord;
import com.cardbattle.entity.User;
import com.cardbattle.mapper.MatchRecordMapper;
import com.cardbattle.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 对局记录查询: 我的战绩统计 + 最近50条对局 */
@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class RecordController {

    private final MatchRecordMapper matchRecordMapper;
    private final UserMapper userMapper;

    /**
     * 我的战绩: 统计 + 对局记录(支持按胜负筛选 + 分页)
     * @param result 0=全部 1=胜 -1=负 2=平
     */
    @GetMapping("/my")
    public Result<Map<String, Object>> my(HttpServletRequest req,
                                          @RequestParam(defaultValue = "0") int result,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int size) {
        Long uid = (Long) req.getAttribute("userId");
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 10;
        int offset = (page - 1) * size;

        User me = userMapper.selectById(uid);
        Map<String, Object> r = new HashMap<>();
        r.put("rating", me.getRating());
        r.put("winCount", me.getWinCount());
        r.put("loseCount", me.getLoseCount());

        // 筛选条件: 谁参与的 + 胜负结果
        var base = new LambdaQueryWrapper<MatchRecord>()
                .and(w -> w.eq(MatchRecord::getPlayer1Id, uid)
                        .or().eq(MatchRecord::getPlayer2Id, uid));
        if (result == 1) base.eq(MatchRecord::getWinnerId, uid);
        else if (result == -1) base.ne(MatchRecord::getWinnerId, uid).isNotNull(MatchRecord::getWinnerId);
        else if (result == 2) base.isNull(MatchRecord::getWinnerId);

        long total = matchRecordMapper.selectCount(base);
        List<MatchRecord> records = matchRecordMapper.selectList(
                new LambdaQueryWrapper<MatchRecord>()
                        .and(w -> w.eq(MatchRecord::getPlayer1Id, uid)
                                .or().eq(MatchRecord::getPlayer2Id, uid))
                        .apply(resultCondition(result, uid))
                        .orderByDesc(MatchRecord::getCreateTime)
                        .last("limit " + offset + "," + size));

        // 批量查对手昵称, 避免循环内逐条查询(N+1问题)
        Set<Long> oppIds = new HashSet<>();
        for (MatchRecord rec : records) {
            oppIds.add(uid.equals(rec.getPlayer1Id()) ? rec.getPlayer2Id() : rec.getPlayer1Id());
        }
        Map<Long, User> users = oppIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(oppIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        List<Map<String, Object>> list = new ArrayList<>();
        for (MatchRecord rec : records) {
            Long oppId = uid.equals(rec.getPlayer1Id()) ? rec.getPlayer2Id() : rec.getPlayer1Id();
            User opp = users.get(oppId);
            Map<String, Object> item = new HashMap<>();
            item.put("id", rec.getId());
            // 人机对战的对手 id 为负数, 库里查不到, 单独给个可读名字
            item.put("opponent", oppId < 0 ? "机器人陪练" : (opp == null ? "未知玩家" : opp.getNickname()));
            item.put("result", rec.getWinnerId() == null ? 0 : (rec.getWinnerId().equals(uid) ? 1 : -1));
            item.put("rounds", rec.getRounds());
            item.put("createTime", rec.getCreateTime());
            list.add(item);
        }
        r.put("list", list);
        r.put("total", total);
        r.put("page", page);
        r.put("size", size);
        return Result.ok(r);
    }

    /** 胜负筛选条件(SQL 片段), uid 为传入的主键, 不存在注入风险 */
    private String resultCondition(int result, Long uid) {
        switch (result) {
            case 1:  return "winner_id = " + uid;
            case -1: return "winner_id is not null and winner_id <> " + uid;
            case 2:  return "winner_id is null";
            default: return "1=1";
        }
    }

    /**
     * 战报回放: 返回某局对战的完整日志时间线(按回合分组)。
     *
     * 【安全】只允许对局参与者查看自己的战报(校验 uid 是 player1 或 player2),
     *         避免通过遍历 id 偷看他人对局过程。
     */
    @GetMapping("/{id}/replay")
    public Result<Map<String, Object>> replay(HttpServletRequest req, @PathVariable Long id) {
        Long uid = (Long) req.getAttribute("userId");
        MatchRecord rec = matchRecordMapper.selectById(id);
        if (rec == null) throw new com.cardbattle.common.BizException("对局不存在");
        if (!uid.equals(rec.getPlayer1Id()) && !uid.equals(rec.getPlayer2Id())) {
            throw new com.cardbattle.common.BizException("无权查看该对局战报");
        }

        Long oppId = uid.equals(rec.getPlayer1Id()) ? rec.getPlayer2Id() : rec.getPlayer1Id();
        User opp = oppId < 0 ? null : userMapper.selectById(oppId);
        User me = userMapper.selectById(uid);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", rec.getId());
        r.put("roomId", rec.getRoomId());
        r.put("rounds", rec.getRounds());
        r.put("createTime", rec.getCreateTime());
        r.put("me", Map.of("id", uid, "nickname", me == null ? "我" : me.getNickname()));
        r.put("opponent", Map.of("id", oppId,
                "nickname", opp == null ? (oppId < 0 ? "机器人陪练" : "未知玩家") : opp.getNickname()));
        r.put("result", rec.getWinnerId() == null ? 0 : (rec.getWinnerId().equals(uid) ? 1 : -1));

        // 日志时间线: [{turn, actor, text}...] 再按回合分组, 便于前端逐回合播放
        List<Map<String, Object>> timeline = new ArrayList<>();
        String detail = rec.getDetail();
        if (detail != null && !detail.isBlank() && !"null".equals(detail)) {
            try {
                com.fasterxml.jackson.databind.JsonNode arr =
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(detail);
                if (arr != null && arr.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode n : arr) {
                        Map<String, Object> e = new LinkedHashMap<>();
                        e.put("turn", n.path("turn").asInt(0));
                        e.put("actor", n.path("actor").asText(""));
                        e.put("text", n.path("text").asText(""));
                        timeline.add(e);
                    }
                }
            } catch (Exception ignore) {
                // detail 不是合法 JSON(历史数据) -> 返回空时间线, 不影响其余字段
            }
        }
        r.put("timeline", timeline);

        // 按回合分组: {1: [...], 2: [...]}
        Map<Integer, List<Map<String, Object>>> byTurn = new LinkedHashMap<>();
        for (Map<String, Object> e : timeline) {
            byTurn.computeIfAbsent((Integer) e.get("turn"), k -> new ArrayList<>()).add(e);
        }
        r.put("turns", byTurn);
        return Result.ok(r);
    }

    /**
     * 生成战报分享码。只允许对局参与者生成 —— 分享是发起者的权利。
     */
    @GetMapping("/{id}/shareCode")
    public Result<Map<String, Object>> shareCode(HttpServletRequest req, @PathVariable Long id) {
        Long uid = (Long) req.getAttribute("userId");
        MatchRecord rec = matchRecordMapper.selectById(id);
        if (rec == null) throw new com.cardbattle.common.BizException("对局不存在");
        if (!uid.equals(rec.getPlayer1Id()) && !uid.equals(rec.getPlayer2Id())) {
            throw new com.cardbattle.common.BizException("无权分享该对局战报");
        }
        Map<String, Object> r = new HashMap<>();
        r.put("id", rec.getId());
        r.put("code", com.cardbattle.util.RecordCodeUtil.encode(rec.getId()));
        return Result.ok(r);
    }

    /**
     * 用分享码查看战报(任何人拿到码都能看, 因此内容是**脱敏**的)。
     *
     * 【安全】这里不校验参与者身份 —— 分享码的用途就是给别人看。
     * 但只返回战斗日志时间线与双方昵称, 不含任何一方手牌, 也不含用户 id。
     */
    @GetMapping("/share/{code}")
    public Result<Map<String, Object>> shareView(@PathVariable String code) {
        long id = com.cardbattle.util.RecordCodeUtil.decode(code);
        MatchRecord rec = matchRecordMapper.selectById(id);
        if (rec == null) throw new com.cardbattle.common.BizException("战报不存在或已被清理");

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", rec.getId());
        r.put("rounds", rec.getRounds());
        r.put("createTime", rec.getCreateTime());
        r.put("result", rec.getWinnerId() == null ? 0 : 1);   // 只给"是否分出胜负", 不给具体谁赢的 id
        r.put("shared", true);

        // 双方昵称(机器人为负数 id, 查不到就显示"机器人陪练")
        User p1 = rec.getPlayer1Id() < 0 ? null : userMapper.selectById(rec.getPlayer1Id());
        User p2 = rec.getPlayer2Id() < 0 ? null : userMapper.selectById(rec.getPlayer2Id());
        r.put("side1", p1 == null ? (rec.getPlayer1Id() < 0 ? "机器人陪练" : "未知玩家") : p1.getNickname());
        r.put("side2", p2 == null ? (rec.getPlayer2Id() < 0 ? "机器人陪练" : "未知玩家") : p2.getNickname());

        // 只取时间线, 且逐条剥掉 actor(actor 是用户 id, 属于不该外泄的信息)
        List<Map<String, Object>> timeline = new ArrayList<>();
        String detail = rec.getDetail();
        if (detail != null && !detail.isBlank() && !"null".equals(detail)) {
            try {
                com.fasterxml.jackson.databind.JsonNode arr =
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(detail);
                if (arr != null && arr.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode n : arr) {
                        Map<String, Object> e = new LinkedHashMap<>();
                        e.put("turn", n.path("turn").asInt(0));
                        e.put("text", n.path("text").asText(""));
                        timeline.add(e);
                    }
                }
            } catch (Exception ignore) {
                // 历史数据不是合法 JSON -> 空时间线
            }
        }
        r.put("timeline", timeline);

        Map<Integer, List<Map<String, Object>>> byTurn = new LinkedHashMap<>();
        for (Map<String, Object> e : timeline) {
            byTurn.computeIfAbsent((Integer) e.get("turn"), k -> new ArrayList<>()).add(e);
        }
        r.put("turns", byTurn);
        return Result.ok(r);
    }
}
