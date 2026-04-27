package com.casy.casyaicodemother.config;

import cn.dev33.satoken.stp.StpInterface;
import com.casy.casyaicodemother.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 自定义权限加载接口实现类
 */
@Component    // 保证此类被 SpringBoot 扫描，完成 Sa-Token 的自定义权限验证扩展 
public class StpInterfaceImpl implements StpInterface {

    @Resource
    UserService userService;

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

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        String userRole = userService.getLoginUser().getUserRole();
        return List.of(userRole.split(","));
    }

}
