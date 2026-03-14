package com.ye94z.user.service;

import com.ye94z.common.core.pojo.Result;
import com.ye94z.user.dto.LoginFormDTO;
import com.ye94z.user.entity.UserAccount;

public interface UserService {
    /** 为指定手机号生成并发送验证码。 */
    Result sendCode(String phone);

    /** 基于手机号 + 验证码登录，成功后返回 JWT。 */
    Result login(LoginFormDTO form);

    /** 按主键查询用户。 */
    UserAccount getById(Long id);
}
