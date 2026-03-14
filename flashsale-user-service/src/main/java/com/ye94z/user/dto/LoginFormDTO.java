package com.ye94z.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginFormDTO {
    /** 登录手机号。 */
    @NotBlank(message = "手机号不能为空")
    private String phone;

    /** 短信验证码。 */
    @NotBlank(message = "验证码不能为空")
    private String code;
}
