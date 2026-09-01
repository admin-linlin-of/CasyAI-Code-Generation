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
 * 输入护轨拦截事件。
 * 每次 Prompt 被安全护轨拒绝时落一条记录，便于审计触发规则、用户信息和处理结果。
 *
 * @author <a href="https://gitee.com/linlinyes/casy-ai-code-mother">程序员Casy</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_guardrail_event")
public class GuardrailEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键，雪花 ID
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    @Column("id")
    private Long id;

    /**
     * 被拦截用户 ID
     */
    @Column("user_id")
    private Long userId;

    /**
     * 用户账号（冗余，避免事后查用户表）
     */
    @Column("user_account")
    private String userAccount;

    /**
     * 用户昵称
     */
    @Column("user_name")
    private String userName;

    /**
     * 当时操作的应用 ID
     */
    @Column("app_id")
    private Long appId;

    /**
     * 请求路径，如 /app/chat/gen/code
     */
    @Column("request_uri")
    private String requestUri;

    /**
     * 客户端 IP（优先 X-Forwarded-For）
     */
    @Column("request_ip")
    private String requestIp;

    /**
     * 被拦截的原始输入（截断后）
     */
    @Column("input_content")
    private String inputContent;

    /**
     * 触发规则类型：LENGTH / EMPTY / SENSITIVE_WORD / INJECTION
     */
    @Column("rule_type")
    private String ruleType;

    /**
     * 命中细节：敏感词、正则或超长统计
     */
    @Column("rule_detail")
    private String ruleDetail;

    /**
     * 返回给前端的错误信息
     */
    @Column("fail_message")
    private String failMessage;

    /**
     * 处理结果：SSE_PUSHED（已通过 SSE 推送）/ JSON_RETURNED（普通接口 JSON）
     */
    @Column("handle_result")
    private String handleResult;

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
}
