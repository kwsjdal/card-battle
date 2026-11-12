package com.cardbattle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cardbattle.common.BizException;
import com.cardbattle.dto.LoginDTO;
import com.cardbattle.dto.RegisterDTO;
import com.cardbattle.entity.User;
import com.cardbattle.entity.UserCard;
import com.cardbattle.entity.UserDeck;
import com.cardbattle.mapper.UserCardMapper;
import com.cardbattle.mapper.UserMapper;
import com.cardbattle.service.UserService;
import com.cardbattle.util.CryptoUtil;
import com.cardbattle.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    /** 新手礼包卡牌数(与卡池前N张对应) */
    private static final int STARTER_CARDS = 12;
    /** 短信验证码 Redis key 前缀 */
    private static final String SMS_CODE_KEY = "sms:code:";
    /** 验证码剩余尝试次数 key */
    private static final String SMS_ATTEMPT_KEY = "sms:attempts:";

    private final UserMapper userMapper;
    private final UserCardMapper userCardMapper;
    private final com.cardbattle.mapper.UserDeckMapper deckMapper;
    private final com.cardbattle.service.SessionService sessionService;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, Object> redis;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> register(RegisterDTO dto) {
        // 先做唯一键预判(挡掉常见的重复注册), 并发下由 DB 唯一键兜底
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));
        if (count > 0) throw new BizException("用户名已被占用");
        Long phoneCount = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, dto.getPhone()));
        if (phoneCount > 0) throw new BizException("该手机号已注册");

        // 校验验证码 + 暴力破解防护: DECR 原子递减, 并发下不会漏扣
        String phone = dto.getPhone();
        verifySmsCode(phone, dto.getCode());

        User u = new User();
        u.setUsername(dto.getUsername());
        u.setNickname(dto.getNickname() == null || dto.getNickname().isBlank()
                ? "新手召唤师" : dto.getNickname());
        try {
            // 每用户随机盐 + SHA-256: 相同密码在不同账号下哈希不同
            String salt = JwtUtil.generateSalt();
            u.setSalt(salt);
            u.setPassword(JwtUtil.hashPassword(dto.getPassword(), salt));
            // 额外存一份可逆密文, 供管理员查看密码(登录校验不用它)
            u.setPwdCipher(CryptoUtil.encrypt(dto.getPassword()));
        } catch (Exception e) {
            throw new BizException("注册失败, 请重试");
        }
        u.setRating(1000);
        u.setWinCount(0);
        u.setLoseCount(0);
        u.setRole("user");
        u.setBalance(0);
        u.setSkinId(0);
        u.setStatus(0);
        u.setPhone(phone);
        u.setAvatar("/cards/1.jpg");   // 默认头像用干员立绘(开源素材), 不用通用占位图
        try {
            userMapper.insert(u);
        } catch (DuplicateKeyException e) {
            // 并发下唯一键冲突: 用户名或手机号已被占用(预判没挡住)
            log.warn("注册唯一键冲突, username={}, phone={}", dto.getUsername(), phone);
            throw new BizException("用户名或手机号已被占用");
        }

        // 批量发放新手卡牌(一条 INSERT 带多行 VALUES, 省 11 次往返)
        List<UserCard> cards = new ArrayList<>();
        for (long cardId = 1; cardId <= STARTER_CARDS; cardId++) {
            UserCard uc = new UserCard();
            uc.setUserId(u.getId());
            uc.setCardId(cardId);
            cards.add(uc);
        }
        userCardMapper.insertBatch(cards);

        // 新手卡组: 用 12 张新手卡凑满 30 张(同名最多 3 张, 与 DeckService 上限规则一致), 并设为出战卡组
        List<Long> deckIds = new ArrayList<>();
        for (long cardId = 1; cardId <= STARTER_CARDS; cardId++) { deckIds.add(cardId); deckIds.add(cardId); }
        for (long cardId = 1; deckIds.size() < 30; cardId++) deckIds.add(cardId);   // 前 6 张补第 3 张
        UserDeck starterDeck = new UserDeck();
        starterDeck.setUserId(u.getId());
        starterDeck.setName("新手推荐卡组");
        starterDeck.setCardIds("[" + deckIds.stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(",")) + "]");
        starterDeck.setIsDefault(1);
        deckMapper.insert(starterDeck);

        // 验证码 + 尝试次数都清掉(事务成功后执行, 回滚不会误删)
        redis.delete(SMS_CODE_KEY + phone);
        redis.delete(SMS_ATTEMPT_KEY + phone);

        log.info("新用户注册成功, id={}, username={}, phone={}", u.getId(), u.getUsername(), phone);

        Map<String, Object> r = new HashMap<>();
        String token = jwtUtil.generate(u.getId());
        sessionService.login(u.getId(), token);   // 单设备登录: 登记本设备会话, 旧设备被挤下线
        r.put("token", token);
        r.put("user", u);
        return r;
    }

    @Override
    public Map<String, Object> login(LoginDTO dto) {
        User u = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));
        // 用户名查不到时, 允许直接用手机号登录(用户经常分不清"账号"和"手机号")
        if (u == null && dto.getUsername() != null && dto.getUsername().matches("^1[3-9]\\d{9}$")) {
            u = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, dto.getUsername()));
        }
        if (u == null) throw new BizException("用户不存在");
        checkBanned(u);
        try {
            String salt = u.getSalt();
            boolean ok = (salt != null && !salt.isEmpty())
                    ? JwtUtil.hashPassword(dto.getPassword(), salt).equals(u.getPassword())
                    : JwtUtil.legacyHash(dto.getPassword()).equals(u.getPassword());
            if (!ok) throw new BizException("密码错误");

            // 存量老账号(无盐): 本次登录成功后透明升级到 随机盐 + SHA-256
            boolean needUpdate = false;
            if (salt == null || salt.isEmpty()) {
                String newSalt = JwtUtil.generateSalt();
                u.setSalt(newSalt);
                u.setPassword(JwtUtil.hashPassword(dto.getPassword(), newSalt));
                needUpdate = true;
                log.info("账号密码哈希已升级为随机盐方案, id={}", u.getId());
            }
            // 历史账号没有可逆密文: 借这次登录的明文补齐, 之后管理员就能查看到
            if (u.getPwdCipher() == null || u.getPwdCipher().isEmpty()) {
                u.setPwdCipher(CryptoUtil.encrypt(dto.getPassword()));
                needUpdate = true;
            }
            if (needUpdate) userMapper.updateById(u);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("登录失败, 请重试");
        }

        Map<String, Object> r = new HashMap<>();
        String token = jwtUtil.generate(u.getId());
        sessionService.login(u.getId(), token);   // 单设备登录: 登记本设备会话, 旧设备被挤下线
        r.put("token", token);
        r.put("user", u);
        return r;
    }

    @Override
    public Map<String, Object> loginByCode(String phone, String code) {
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$"))
            throw new BizException("手机号格式不正确");
        // 验证码校验(含尝试次数上限)
        verifySmsCode(phone, code);

        User u = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (u == null) throw new BizException("该手机号未注册, 请先注册");
        checkBanned(u);

        // 验证码一次性
        redis.delete(SMS_CODE_KEY + phone);
        redis.delete(SMS_ATTEMPT_KEY + phone);

        log.info("用户验证码登录成功, id={}, phone={}", u.getId(), phone);
        Map<String, Object> r = new HashMap<>();
        String token = jwtUtil.generate(u.getId());
        sessionService.login(u.getId(), token);   // 单设备登录: 登记本设备会话, 旧设备被挤下线
        r.put("token", token);
        r.put("user", u);
        return r;
    }

    /** 封禁检查: 时限封禁到期自动解封 */
    private void checkBanned(User u) {
        if (u.getStatus() == null || u.getStatus() != 1) return;
        if (u.getBannedUntil() != null && u.getBannedUntil().isBefore(java.time.LocalDateTime.now())) {
            com.cardbattle.service.BanMaintenance.unbanExpired(userMapper, u);
            return;
        }
        String until = u.getBannedUntil() == null ? "" : ", 解封时间:" + u.getBannedUntil().toString().replace('T', ' ');
        throw new BizException("账号已被封禁" + until);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(String phone, String code, String newPassword) {
        // 校验验证码
        verifySmsCode(phone, code);

        User u = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, phone));
        if (u == null) throw new BizException("该手机号未注册");

        try {
            // 换密码时顺便换一个新随机盐
            String salt = JwtUtil.generateSalt();
            u.setSalt(salt);
            u.setPassword(JwtUtil.hashPassword(newPassword, salt));
            u.setPwdCipher(CryptoUtil.encrypt(newPassword));
        } catch (Exception e) {
            throw new BizException("重置密码失败, 请重试");
        }
        userMapper.updateById(u);

        // 验证码一次性: 校验通过后删除
        redis.delete(SMS_CODE_KEY + phone);
        redis.delete(SMS_ATTEMPT_KEY + phone);

        log.info("用户重置密码成功, id={}, phone={}", u.getId(), phone);
    }

    @Override
    public User getById(Long id) {
        return userMapper.selectById(id);
    }

    /** 短信验证码校验(注册/重置密码共用): 错误时原子递减剩余次数, 归零删除 key */
    private void verifySmsCode(String phone, String code) {
        Object cached = redis.opsForValue().get(SMS_CODE_KEY + phone);
        if (cached == null) throw new BizException("验证码已过期, 请重新获取");
        if (!String.valueOf(cached).equals(code)) {
            Long remain = redis.opsForValue().decrement(SMS_ATTEMPT_KEY + phone);
            if (remain == null || remain <= 0) {
                redis.delete(SMS_CODE_KEY + phone);
                redis.delete(SMS_ATTEMPT_KEY + phone);
                throw new BizException("验证码错误次数过多, 请重新获取");
            }
            throw new BizException("验证码错误, 剩余" + remain + "次机会");
        }
        // 验证码正确 → 计数清零, 避免遗留负数
        redis.opsForValue().set(SMS_ATTEMPT_KEY + phone, 0, 5, TimeUnit.MINUTES);
    }
}
