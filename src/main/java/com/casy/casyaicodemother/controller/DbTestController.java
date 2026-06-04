package com.casy.casyaicodemother.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DbTestController {

    @Resource
    private JdbcTemplate jdbcTemplate;

    // 测试接口
    @Operation(summary = "测试PostgreSQL连接")
    @GetMapping("/test-postgres")
    public String testConnection() {
        // 查询 PostgreSQL 系统表数量
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM pg_tables", Long.class);
        return "✅ PostgreSQL 连接成功！系统表数量：" + count;
    }
}