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
 * 应用代码版本 实体类。
 *
 * @author <a href="https://gitee.com/linlinyes/casy-ai-code-mother">程序员Casy</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "t_app_version", schema = "public")
public class AppVersion implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    @Column("id")
    private Long id;

    /**
     * 应用id
     */
    @Column("app_id")
    private Long appId;

    /**
     * 关联的AI对话消息id
     */
    @Column("chat_history_id")
    private Long chatHistoryId;

    /**
     * 版本号，从1递增
     */
    @Column("version_num")
    private Integer versionNum;

    /**
     * 代码目录，如 v1、v2
     */
    @Column("code_dir")
    private String codeDir;

    /**
     * 生成该版本使用的AI模型
     */
    @Column("model_type")
    private String modelType;

    /**
     * 构建状态：pending/building/success/failed
     */
    @Column("build_status")
    private String buildStatus;

    /**
     * 构建失败原因（npm 输出摘要）
     */
    @Column("build_error")
    private String buildError;

    /**
     * 部署状态：not_deployed/deploying/success/failed
     */
    @Column("deploy_status")
    private String deployStatus;

    /**
     * 创建用户id
     */
    @Column("user_id")
    private Long userId;

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
     * 是否删除
     */
    @Column("is_delete")
    private Integer isDelete;

}
