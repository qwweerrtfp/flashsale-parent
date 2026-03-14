package com.ye94z.user.mapper;

import com.ye94z.user.entity.UserAccount;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserAccountMapper {

    /** 按用户主键查询。 */
    @Select("""
            SELECT id, phone, password_hash AS passwordHash, nickname, avatar_url AS avatarUrl,
                   status, create_time AS createTime, update_time AS updateTime
            FROM user_account
            WHERE id = #{id}
            """)
    UserAccount findById(@Param("id") Long id);

    /** 按手机号查询，登录场景会频繁使用。 */
    @Select("""
            SELECT id, phone, password_hash AS passwordHash, nickname, avatar_url AS avatarUrl,
                   status, create_time AS createTime, update_time AS updateTime
            FROM user_account
            WHERE phone = #{phone}
            """)
    UserAccount findByPhone(@Param("phone") String phone);

    /** 插入新用户，并通过 useGeneratedKeys 回填数据库生成的主键。 */
    @Insert("""
            INSERT INTO user_account(phone, password_hash, nickname, avatar_url, status)
            VALUES(#{phone}, #{passwordHash}, #{nickname}, #{avatarUrl}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserAccount ua);

    /** 更新用户昵称和头像。当前项目中暂未暴露对应控制器。 */
    @Update("""
            UPDATE user_account
            SET nickname = #{nickname},
                avatar_url = #{avatarUrl},
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateProfile(@Param("id") Long id,
                      @Param("nickname") String nickname,
                      @Param("avatarUrl") String avatarUrl);
}
