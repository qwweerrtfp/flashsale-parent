package com.ye94z.user.mapper;

import com.ye94z.user.entity.UserAccount;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserAccountMapper {

    @Select("""
            SELECT id, phone, password_hash AS passwordHash, nickname, avatar_url AS avatarUrl,
                   status, create_time AS createTime, update_time AS updateTime
            FROM user_account
            WHERE id = #{id}
            """)
    UserAccount findById(@Param("id") Long id);

    @Select("""
            SELECT id, phone, password_hash AS passwordHash, nickname, avatar_url AS avatarUrl,
                   status, create_time AS createTime, update_time AS updateTime
            FROM user_account
            WHERE phone = #{phone}
            """)
    UserAccount findByPhone(@Param("phone") String phone);

    @Insert("""
            INSERT INTO user_account(phone, password_hash, nickname, avatar_url, status)
            VALUES(#{phone}, #{passwordHash}, #{nickname}, #{avatarUrl}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserAccount ua);

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