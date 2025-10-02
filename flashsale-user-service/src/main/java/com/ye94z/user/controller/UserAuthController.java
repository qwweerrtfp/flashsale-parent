package com.ye94z.user.controller;


import com.ye94z.user.dto.LoginFormDTO;
import com.ye94z.user.entity.UserAccount;
import com.ye94z.user.service.UserService;
import com.ye94z.common.core.pojo.Result;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserAuthController {

    private final UserService userService;

    public UserAuthController(UserService userService) {
        this.userService = userService;
    }

    /** 发送短信验证码（仅测试日志） */
    @PostMapping("/send-code")
    public Result sendCode(@RequestParam("phone") String phone) {
        if(phone.isBlank()) return Result.fail("手机号不能为空");
        return userService.sendCode(phone);
    }

    /** 短信验证码登录，返回 JWT */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO form) {
        return userService.login(form);
    }

    /** 简单查询（供内部/联调使用） */
    @GetMapping("/{id}")
    public Result get(@PathVariable("id") Long id) {
        UserAccount ua = userService.getById(id);
        return ua == null ? Result.fail("不存在") : Result.ok(ua);
    }
}