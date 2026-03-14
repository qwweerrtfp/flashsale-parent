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

    /**
     * 发送短信验证码。
     * 当前项目里没有接入真实短信通道，所以验证码会同时返回给调用方并打印到日志里，
     * 方便本地联调。
     */
    @PostMapping("/send-code")
    public Result sendCode(@RequestParam("phone") String phone) {
        if(phone.isBlank()) return Result.fail("手机号不能为空");
        return userService.sendCode(phone);
    }

    /**
     * 验证码登录。
     * 登录成功后返回 JWT，后续请求统一由网关解析并转发用户身份。
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO form) {
        return userService.login(form);
    }

    /** 简单用户查询接口，主要用于联调或服务内部调试。 */
    @GetMapping("/{id}")
    public Result get(@PathVariable("id") Long id) {
        UserAccount ua = userService.getById(id);
        return ua == null ? Result.fail("不存在") : Result.ok(ua);
    }
}
