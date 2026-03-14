package com.ye94z.common.core.constants;

/**
 * 系统级通用常量。
 * 这类常量不应该绑定到某个具体服务，否则后续模块复用时容易出现重复定义。
 */
public final class SystemConstants {

    private SystemConstants() {}

    /** 默认分页大小。当前项目里更多是示例值，不是严格上限。 */
    public static final int MAX_PAGE_SIZE = 10;

    /** 新用户自动注册时使用的昵称前缀。 */
    public static final String USER_NICK_NAME_PREFIX = "user_";
}
