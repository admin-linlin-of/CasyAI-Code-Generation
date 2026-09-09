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

/**
 * 系统参数，对应表 t_sys_param。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_sys_param")
public class SysParam implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /** 参数键，如 site.github.url */
    @Column("param_key")
    private String paramKey;

    /** 参数值 */
    @Column("param_value")
    private String paramValue;

    /** 展示名称 */
    @Column("param_name")
    private String paramName;

    /** 备注 */
    @Column("remark")
    private String remark;

    /** 1 启用，0 停用 */
    @Column("enabled")
    private Integer enabled;

    /** 1 可被未登录接口读取（首页图标等） */
    @Column("is_public")
    private Integer isPublic;

    @Column("sort_order")
    private Integer sortOrder;

    @Column("create_time")
    private Timestamp createTime;

    @Column("update_time")
    private Timestamp updateTime;

    @Column(value = "is_delete", isLogicDelete = true)
    private Integer isDelete;
}
