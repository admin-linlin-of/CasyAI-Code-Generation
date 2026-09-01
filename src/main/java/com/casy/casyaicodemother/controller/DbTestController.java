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

    @Operation(summary = "测试MySQL连接")
    @GetMapping("/test-mysql")
    public String testConnection() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()",
                Long.class);
        return "✅ MySQL 连接成功！当前库表数量：" + count;
    }
}
