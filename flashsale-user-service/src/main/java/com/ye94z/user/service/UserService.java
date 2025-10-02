package com.ye94z.user.service;

import com.ye94z.common.core.pojo.Result;
import com.ye94z.user.dto.LoginFormDTO;
import com.ye94z.user.entity.UserAccount;

public interface UserService {
    Result sendCode(String phone);

    Result login(LoginFormDTO form);

    UserAccount getById(Long id);
}