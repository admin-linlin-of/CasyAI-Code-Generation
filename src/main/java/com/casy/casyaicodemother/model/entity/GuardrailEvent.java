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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "t_guardrail_event", schema = "public")
public class GuardrailEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    @Column("id")
    private Long id;

    @Column("user_id")
    private Long userId;

    @Column("user_account")
    private String userAccount;

    @Column("user_name")
    private String userName;

    @Column("app_id")
    private Long appId;

    @Column("request_uri")
    private String requestUri;

    @Column("request_ip")
    private String requestIp;

    @Column("input_content")
    private String inputContent;

    @Column("rule_type")
    private String ruleType;

    @Column("rule_detail")
    private String ruleDetail;

    @Column("fail_message")
    private String failMessage;

    @Column("handle_result")
    private String handleResult;

    @Column("create_time")
    private Timestamp createTime;

    @Column("update_time")
    private Timestamp updateTime;

    @Column(value = "is_delete", isLogicDelete = true)
    private Integer isDelete;
}
