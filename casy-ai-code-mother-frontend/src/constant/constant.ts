/**
 * 用户角色枚举，与后端 UserConstant.ADMIN_ROLE / DEFAULT_ROLE 保持一致
 * 前端路由 meta.roles 和后端 @SaCheckRole 均使用这些值
 */
const ROLE_ENUM = {
  USER: 'user',
  ADMIN: 'admin',
}

export const APP_FEATURED_PRIORITY = 99
export const APP_NOT_PUBLISH = 0
export const APP_PUBLISHED = 1

export default ROLE_ENUM
