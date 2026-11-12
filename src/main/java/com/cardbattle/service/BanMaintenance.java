package com.cardbattle.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cardbattle.entity.User;
import com.cardbattle.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 封禁维护: 到期自动解封(兜底定时任务)
 *  登录/请求链路已做惰性解封, 此任务保证管理端用户列表状态及时准确 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BanMaintenance {

    private final UserMapper userMapper;

    /** 到期解封: status=1 且 banned_until 已过期的账号恢复正常 */
    public static void unbanExpired(UserMapper userMapper, User u) {
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, u.getId())
                .set(User::getStatus, 0)
                .set(User::getBannedUntil, null)
                .set(User::getBanReason, null));
    }

    @Scheduled(fixedDelay = 60_000)
    public void sweep() {
        int n = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getStatus, 1)
                .isNotNull(User::getBannedUntil)
                .lt(User::getBannedUntil, LocalDateTime.now())
                .set(User::getStatus, 0)
                .set(User::getBannedUntil, null)
                .set(User::getBanReason, null));
        if (n > 0) log.info("定时解封: {}个到期账号已恢复正常", n);
    }
}
