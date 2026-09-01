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
 * 对话历史 实体类。
 *
 * @author <a href="https://gitee.com/linlinyes/casy-ai-code-mother">程序员Casy</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_chat_history")
public class ChatHistory implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    @Column("id")
    private Long id;

    /**
     * 消息
     */
    @Column("message")
    private String message;

    /**
     * user/ai
     */
    @Column("message_type")
    private String messageType;

    /**
     * 应用id
     */
    @Column("app_id")
    private Long appId;

    /**
     * 创建用户id
     */
    @Column("user_id")
    private Long userId;

    /**
     * 父消息id
     */
    @Column("parent_id")
    private Long parentId;

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
    @Column(value = "is_delete", isLogicDelete = true)
    private Integer isDelete;

}
