package com.casy.casyaicodemother.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * AI 模型目录，对应表 t_ai_model。
 * <p>
 * 代码里的 {@link com.casy.casyaicodemother.model.enums.ModelTypeEnum} 只表示「系统能接哪些模型」；
 * 本表决定「当前对外真正开放哪些」。停用不改枚举、不重启即可生效。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "t_ai_model", schema = "public")
public class AiModel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    /** 与 ModelTypeEnum 枚举名一致，如 GPT、DEEPSEEKFLASH */
    @Column("model_code")
    private String modelCode;

    /** 实际调用名，如 gpt-5.5、deepseek-v4-flash */
    @Column("model_name")
    private String modelName;

    /** 1 可用，0 停用；路由 prompt 和指定模型校验都看这个字段 */
    @Column("enabled")
    private Integer enabled;

    /** 1 表示路由失败或选中已停用模型时的回退项，启用中建议只标一条 */
    @Column("is_default")
    private Integer isDefault;

    /** 越小越便宜；多条件同时命中时按此顺序选更便宜的模型 */
    @Column("sort_order")
    private Integer sortOrder;

    /** 写入路由 prompt 的模型能力说明 */
    @Column("description")
    private String description;

    /** 写入路由 prompt 的选择规则，例如「简单页面选 FLASH」 */
    @Column("routing_rule")
    private String routingRule;

    @Column("create_time")
    private Timestamp createTime;

    @Column("update_time")
    private Timestamp updateTime;

    @Column(value = "is_delete", isLogicDelete = true)
    private Integer isDelete;
}
