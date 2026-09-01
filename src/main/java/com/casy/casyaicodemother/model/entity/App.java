package com.casy.casyaicodemother.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.handler.Fastjson2TypeHandler;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 应用 实体类。
 *
 * @author <a href="https://gitee.com/linlinyes/casy-ai-code-mother">程序员Casy</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_app")
public class App implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    @Column("id")
    private Long id;


    /**
     * 应用名称
     */
    @Column("app_name")
    private String appName;

    /**
     * 应用封面
     */
    @Column("cover")
    private String cover;

    /**
     * 应用初始化的 prompt
     */
    @Column("init_prompt")
    private String initPrompt;

    /**
     * 代码生成类型（枚举）
     */
    @Column("code_gen_type")
    private String codeGenType;

    /**
     * 应用类型数组
     */
    @Column(value = "app_types", typeHandler = Fastjson2TypeHandler.class)
    private List<String> appTypes;

    /**
     * 部署标识
     */
    @Column("deploy_key")
    private String deployKey;

    /**
     * 部署时间
     */
    @Column("deployed_time")
    private LocalDateTime deployedTime;

    /**
     * 优先级
     */
    @Column("priority")
    private Integer priority;

    /**
     * 是否公布 0-不公布 1-公布
     */
    @Column("is_publish")
    private Integer isPublish;

    /**
     * 创建用户id
     */
    @Column("user_id")
    private Long userId;

    /**
     * 编辑时间
     */
    @Column("edit_time")
    private LocalDateTime editTime;

    /**
     * 创建时间
     */
    @Column("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column("update_time")
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    @Column(value = "is_delete", isLogicDelete = true)
    private Integer isDelete;

}
