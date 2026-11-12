package com.cardbattle.controller;

import com.cardbattle.common.BizException;
import com.cardbattle.common.Result;
import com.cardbattle.entity.User;
import com.cardbattle.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 个人资料: 预设头像列表 / 更换头像 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    /** 可选头像 = 12 位干员的立绘(取卡面上半部分的面部区域展示) */
    private static final int[] AVATAR_CARD_IDS = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };

    private final UserMapper userMapper;
    private final com.cardbattle.service.StatsService statsService;

    /** 个人数据中心: 战绩 / 收藏 / 消费 / 近14天趋势 */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(HttpServletRequest req) {
        return Result.ok(statsService.overview((Long) req.getAttribute("userId")));
    }

    /** 预设头像列表: 明日方舟干员立绘(开源素材), 而不是通用占位图 */
    @GetMapping("/avatars")
    public Result<List<String>> avatars() {
        List<String> list = new ArrayList<>();
        for (int id : AVATAR_CARD_IDS) list.add("/cards/" + id + ".jpg");
        return Result.ok(list);
    }

    /** 更换头像(仅允许列表内的干员立绘, 或旧的预设头像) */
    @PostMapping("/avatar")
    public Result<String> updateAvatar(HttpServletRequest req, @RequestBody Map<String, String> body) {
        Long userId = (Long) req.getAttribute("userId");
        String avatar = body.get("avatar");
        if (avatar == null || !(avatar.matches("^/cards/\\d+\\.jpg$") || avatar.matches("^/avatars/([1-9]|10)\\.png$")))
            throw new BizException("头像不合法");

        User u = userMapper.selectById(userId);
        if (u == null) throw new BizException("用户不存在");
        u.setAvatar(avatar);
        userMapper.updateById(u);
        return Result.ok(avatar);
    }
}
