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

    /** Redis 主要用于存验证码，不引入用户登录态缓存，保持 JWT 无状态。 */
    private final StringRedisTemplate redis;
    /** 用户表访问入口。 */
    private final UserAccountMapper userAccountMapper;

    public UserServiceImpl(StringRedisTemplate redis, UserAccountMapper userAccountMapper) {
        this.redis = redis;
        this.userAccountMapper = userAccountMapper;
    }

    @Override
    public Result sendCode(String phone) {
        // 1) 先做最基础的格式校验，避免把明显非法的手机号写进 Redis。
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        // 2) 生成 6 位数字验证码，便于测试与手工输入。
        String code = MyUtils.randomNumbers(6);
        // 3) 写入 Redis，并通过 TTL 自动过期。
        redis.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code,
                RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 4) 示例项目里不接短信服务，直接打印日志并返回给调用方。
        log.info("[SMS] phone={}, code={}", phone, code);
        return Result.ok(code);
    }

    @Override
    public Result login(LoginFormDTO form) {
        String phone = form.getPhone();
        String inputCode = form.getCode();

        // 1) 校验手机号格式，避免无意义地访问 Redis/DB。
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        // 2) 从 Redis 取出验证码，对比用户输入。
        String cached = redis.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        if (cached == null || !cached.equals(inputCode)) {
            return Result.fail("验证码错误或已过期");
        }

        // 3) 查找已有用户；首次登录则自动注册。
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
                // 极端并发下可能多个登录请求同时创建同一手机号，这里回查即可收敛。
                ua = userAccountMapper.findByPhone(phone);
            }
        }
        // 用户被禁用时不允许登录。
        if (ua == null || ua.getStatus() != 1) {
            return Result.fail("用户不可用");
        }

        // 4) 组装 JWT。这里把最常用的用户基础信息直接塞进 claims，
        // 后续网关可直接解 token 并向下游透传。
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", ua.getId());
        claims.put("nickname", ua.getNickname());
        claims.put("avatarUrl", ua.getAvatarUrl());
        String token = JwtUtils.generateToken(claims);

        // 5) 登录成功后立即删除验证码，避免同一验证码被重复消费。
        redis.delete(RedisConstants.LOGIN_CODE_KEY + phone);

        return Result.ok(token);
    }

    @Override
    public UserAccount getById(Long id) {
        // 这里不做额外包装，直接让控制器决定返回 Result 还是空值提示。
        return userAccountMapper.findById(id);
    }
}
