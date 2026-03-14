package com.ye94z.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UserServiceApplication {

	public static void main(String[] args) {
		// 启动用户服务，负责验证码登录与用户基础信息查询。
		SpringApplication.run(UserServiceApplication.class, args);
	}

}
