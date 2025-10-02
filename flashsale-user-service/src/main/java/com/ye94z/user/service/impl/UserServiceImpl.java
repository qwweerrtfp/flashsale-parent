package com.ye94z.user.service.impl;

import com.ye94z.common.core.constants.RedisConstants;
import com.ye94z.common.core.constants.SystemConstants;
import com.ye94z.common.core.pojo.Result;
import com.ye94z.common.core.utils.MyUtils;
import com.ye94z.common.core.utils.RegexUtils;
import com.ye94z.payment.security.utils.JwtUtils;
import com.ye94z.user.dto.LoginFormDTO;
import com.ye94z.user.entity.UserAccount;
import com.ye94z.user.mapper.UserAccountMapper;
import com.ye94z.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private final StringRedisTemplate redis;
    private final UserAccountMapper userAccountMapper;

    public UserServiceImpl(StringRedisTemplate redis, UserAccountMapper userAccountMapper) {
        this.redis = redis;
        this.userAccountMapper = userAccountMapper;
    }

    @Override
    public Result sendCode(String phone) {
        // 1) 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        // 2) 生成 6 位数字验证码
        String code = MyUtils.randomNumbers(6);
        // 3) 存入 Redis（5 分钟）
        redis.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code,
                RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 4) 实际项目应调用短信通道；这里仅打印日志
        log.info("[SMS] phone={}, code={}", phone, code);
        return Result.ok(code);
    }

    @Override
    public Result login(LoginFormDTO form) {
        String phone = form.getPhone();
        String inputCode = form.getCode();

        // 1) 校验手机
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        // 2) 校验验证码
        String cached = redis.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        if (cached == null || !cached.equals(inputCode)) {
            return Result.fail("验证码错误或已过期");
        }

        // 3) 查或建用户
        UserAccount ua = userAccountMapper.findByPhone(phone);
        if (ua == null) {
            ua = new UserAccount();
            ua.setPhone(phone);
            ua.setNickname(SystemConstants.USER_NICK_NAME_PREFIX + MyUtils.randomString(8));
            ua.setAvatarUrl("");
            ua.setStatus((short)1);
            try {
                userAccountMapper.insert(ua);
            } catch (DuplicateKeyException e) {
                // 极端并发：被别人先插入了，回查一次
                ua = userAccountMapper.findByPhone(phone);
            }
        }
        if (ua == null || ua.getStatus() != 1) {
            return Result.fail("用户不可用");
        }

        // 4) 组装 JWT（无状态）
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", ua.getId());
        claims.put("nickname", ua.getNickname());
        claims.put("avatarUrl", ua.getAvatarUrl());
        String token = JwtUtils.generateToken(claims);

        // 5) 可选：删除验证码，减少被重复使用
        redis.delete(RedisConstants.LOGIN_CODE_KEY + phone);

        return Result.ok(token);
    }

    @Override
    public UserAccount getById(Long id) {
        return userAccountMapper.findById(id);
    }
}