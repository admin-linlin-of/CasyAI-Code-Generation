package com.casy.casyaicodemother.util;

import com.casy.casyaicodemother.constant.AppConstant;
import com.casy.casyaicodemother.constant.UserConstant;
import com.casy.casyaicodemother.model.entity.App;
import com.casy.casyaicodemother.model.entity.User;

/**
 * 应用访问权限：精选已公布案例可查看，继续对话仅创建者。
 */
public final class AppAccessUtils {

    private AppAccessUtils() {
    }

    public static boolean isOwner(App app, User loginUser) {
        return app != null && loginUser != null
                && app.getUserId() != null
                && app.getUserId().equals(loginUser.getId());
    }

    public static boolean isAdmin(User loginUser) {
        return loginUser != null && UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
    }

    /**
     * 已公布的精选案例（priority ≥ 99 且 isPublish = 1）。
     */
    public static boolean isFeaturedPublished(App app) {
        if (app == null) {
            return false;
        }
        boolean featured = app.getPriority() != null
                && app.getPriority() >= AppConstant.GOOD_APP_PRIORITY;
        return featured && AppConstant.APP_PUBLISHED.equals(app.getIsPublish());
    }

    /**
     * 查看对话历史、版本与代码预览：创建者、管理员、已公布精选案例。
     */
    public static boolean canViewAppContent(App app, User loginUser) {
        if (app == null || loginUser == null) {
            return false;
        }
        return isAdmin(loginUser) || isOwner(app, loginUser) || isFeaturedPublished(app);
    }
}
