package com.casy.casyaicodemother.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;

/**
 * 用户 实体类。
 *
 * @author <a href="https://gitee.com/linlinyes/casy-ai-code-mother">程序员Casy</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_user")
public class User implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.flexId)
    private Long id;

    /**
     * 用户账号
     */
    @Column("user_account")
    private String userAccount;

    /**
     * 用户密码
     */
    @Column("user_password")
    private String userPassword;

    /**
     * 用户昵称
     */
    @Column("user_name")
    private String userName;

    /**
     * 用户头像
     */
    @Column("user_avatar")
    private String userAvatar;

    /**
     * 用户简介
     */
    @Column("user_profile")
    private String userProfile;

    /**
     * 用户角色：user/admin
     */
    @Column("user_role")
    private String userRole;

    /**
     * 会员过期时间
     */
    @Column("vip_expire_time")
    private Timestamp vipExpireTime;

    /**
     * 会员兑换码
     */
    @Column("vip_code")
    private String vipCode;

    /**
     * 会员编号
     */
    @Column("vip_number")
    private Long vipNumber;

    /**
     * 分享码
     */
    @Column("share_code")
    private String shareCode;

    /**
     * 邀请用户ID
     */
    @Column("invite_user")
    private Long inviteUser;

    /**
     * 编辑时间
     */
    @Column("edit_time")
    private Timestamp editTime;

    /**
     * 创建时间
     */
    @Column("create_time")
    private Timestamp createTime;

    /**
     * 更新时间
     */
    @Column("update_time")
    private Timestamp updateTime;

    /**
     * 是否删除 0-未删除 1-已删除
     */
    @Column(value = "is_delete", isLogicDelete = true)
    private Integer isDelete;

    @Column(ignore = true)
    private List<String> permissions;
}
