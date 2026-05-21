package com.casy.casyaicodemother.config;

import cn.dev33.satoken.stp.StpInterface;
import com.casy.casyaicodemother.model.entity.User;
import com.casy.casyaicodemother.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Sa-Token 权限数据源（唯一来源）
 *
 * 角色 role → 对应 @SaCheckRole，同时下发给前端 LoginUserVO.userRole
 * 权限 permission → 对应 @SaCheckPermission，同时下发给前端 LoginUserVO.permissions
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Resource
    UserService userService;

    /**
     * 根据角色推导 permission 列表
     * admin 拥有 "*" 表示全部接口权限；user 仅拥有 "user.get"
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        List<String> roles = getRoleList(loginId, loginType);
        Set<String> perms = new LinkedHashSet<>();
        if (roles.contains("admin")) {
            perms.add("*");
        }
        if (roles.contains("user")) {
            perms.add("user.get");
        }
        return new ArrayList<>(perms);
    }

    /**
     * 通过 loginId 查库获取角色，避免调用 getLoginUser() 造成循环依赖和多余查询
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        User user = userService.getById(Long.valueOf(loginId.toString()));
        if (user == null || user.getUserRole() == null) {
            return List.of();
        }
        return List.of(user.getUserRole().split(","));
    }

}
