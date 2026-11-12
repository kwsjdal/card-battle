package com.cardbattle.controller;

import com.cardbattle.common.BizException;
import com.cardbattle.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/** 虚拟短信验证码(演示用: 不真实发送, 直接返回验证码)
 *  安全: 60s 发送冷却 + 验证码5分钟过期 + 注册端有次数限制(见 UserServiceImpl.register) */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class SmsController {

    public static final String SMS_CODE_KEY = "sms:code:";
    private static final String SMS_LOCK_KEY = "sms:lock:";   // 发送冷却锁
    private static final String SMS_ATTEMPT_KEY = "sms:attempts:";  // 剩余尝试次数
    public static final String SMS_TRACE_KEY = "sms:trace:";  // 验证码流水(仅供管理员查阅, 不下发前端)
    private static final String SMS_DAY_KEY = "sms:day:";     // 单手机号每日发送上限
    private static final int MAX_ATTEMPTS_INITIAL = 5;
    private static final int MAX_PER_DAY = 10;                // 每个手机号每天最多 10 条
    private static final long SMS_EXPIRE_MIN = 5;
    private static final long SMS_LOCK_SEC = 60;

    private final RedisTemplate<String, Object> redis;
    private final com.cardbattle.mapper.UserMapper userMapper;

    /**
     * 演示开关: 毕业答辩演示时若无短信网关, 可把 cardbattle.sms.echo-code 配成 true,
     * 接口会额外把验证码回显给前端(等同于"短信发到你手机上")。默认 false = 不下发明文。
     */
    @org.springframework.beans.factory.annotation.Value("${cardbattle.sms.echo-code:false}")
    private boolean echoCode;

    /**
     * 发送验证码
     * scene: register=注册(该手机号必须未注册) / login=验证码登录 / reset=找回密码(后两者要求已注册)
     */
    @PostMapping("/sms")
    public Result<Map<String, Object>> send(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String scene = body.getOrDefault("scene", "register");
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$"))
            throw new BizException("手机号格式不正确(11 位)");

        // 一个手机号只能注册一次: 发送前先判重, 让用户立刻看到提示
        boolean exists = userMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.cardbattle.entity.User>()
                .eq(com.cardbattle.entity.User::getPhone, phone)) > 0;
        if ("register".equals(scene) && exists)
            throw new BizException("该手机号已注册, 请直接登录");
        if (!"register".equals(scene) && !exists)
            throw new BizException("该手机号未注册, 请先注册");

        // 防刷: SETNX 原子锁定, 60 秒内同一手机号只能发一次 (check-then-act 竞态修复)
        Boolean locked = redis.opsForValue().setIfAbsent(SMS_LOCK_KEY + phone, "1", SMS_LOCK_SEC, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked))
            throw new BizException("发送过于频繁, 请稍后再试");

        // 每日发送上限: 防止被用于短信轰炸(真实环境还要按 IP 限流)
        String dayKey = SMS_DAY_KEY + phone + ":" + LocalDate.now();
        Long sent = redis.opsForValue().increment(dayKey);
        if (sent != null && sent == 1) redis.expire(dayKey, 25, TimeUnit.HOURS);
        if (sent != null && sent > MAX_PER_DAY) {
            throw new BizException("该手机号今日验证码发送次数已达上限, 请明天再试");
        }

        // 注: 上面的查重只是"提前给出友好提示", 真正的唯一性仍由 register() + uk_phone 唯一索引兜底
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
        redis.opsForValue().set(SMS_CODE_KEY + phone, code, SMS_EXPIRE_MIN, TimeUnit.MINUTES);
        redis.opsForValue().set(SMS_ATTEMPT_KEY + phone, MAX_ATTEMPTS_INITIAL, SMS_EXPIRE_MIN, TimeUnit.MINUTES);
        // 验证码流水(仅管理员可查): 毕业演示环境没有真实短信网关, 管理员可在此查看
        redis.opsForList().rightPush(SMS_TRACE_KEY + phone,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")) + " " + code + " (" + scene + ")");
        redis.expire(SMS_TRACE_KEY + phone, SMS_EXPIRE_MIN, TimeUnit.MINUTES);
        redis.opsForList().trim(SMS_TRACE_KEY + phone, -5, -1);   // 只保留最近 5 条

        // 日志脱敏: 手机号中间 4 位打码
        log.info("验证码已生成, phone={}, scene={}, expire={}min, code={}", mask(phone), scene, SMS_EXPIRE_MIN, code);

        Map<String, Object> r = new HashMap<>();
        r.put("phone", mask(phone));
        r.put("expire", SMS_EXPIRE_MIN * 60);
        r.put("scene", scene);
        // 安全: 明文验证码默认不再随响应下发给前端, 应由短信网关投递
        r.put("tip", "验证码已发送至 " + mask(phone) + ", 5 分钟内有效");
        if (echoCode) {
            r.put("code", code);        // 仅演示开关打开时回显, 生产环境必须关闭
            r.put("echo", true);
        }
        return Result.ok(r);
    }

    /** 手机号脱敏: 138****8888 */
    public static String mask(String phone) {
        if (phone == null || phone.length() != 11) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
