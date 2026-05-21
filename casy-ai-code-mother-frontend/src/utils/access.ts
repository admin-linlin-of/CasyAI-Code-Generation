/**
 * 前端权限工具（方案 A：页面按角色控制，接口按 Sa-Token permission 控制）
 *
 * - userRole：来自后端 LoginUserVO，用于路由守卫 / 菜单显隐（对应 @SaCheckRole）
 * - permissions：来自 Sa-Token，用于按钮级或接口级权限（对应 @SaCheckPermission）
 */
import type { RouteMeta } from 'vue-router'
import ROLE_ENUM from '@/constant/constant'

/** 解析 userRole 字段，支持 "admin,user" 多角色 */
export function getUserRoles(loginUser: API.LoginUserVO): string[] {
  if (!loginUser.userRole) {
    return []
  }
  return loginUser.userRole.split(',').map((role) => role.trim())
}

/**
 * 判断用户是否拥有指定角色
 * admin 拥有所有页面访问权（与后端 @SaCheckRole 的管理员逻辑一致）
 */
export function hasRole(loginUser: API.LoginUserVO, ...roles: string[]): boolean {
  const userRoles = getUserRoles(loginUser)
  if (userRoles.includes(ROLE_ENUM.ADMIN)) {
    return true
  }
  return roles.some((role) => userRoles.includes(role))
}

/**
 * 判断用户是否拥有指定接口权限码
 * 对应后端 StpInterfaceImpl 下发的 permission 列表
 */
export function hasPermission(loginUser: API.LoginUserVO, permission: string): boolean {
  const permissions = loginUser.permissions
  if (!permissions?.length) {
    return false
  }
  // admin 的 permission 列表包含 "*"，表示拥有全部接口权限
  if (permissions.includes('*')) {
    return true
  }
  return permissions.includes(permission)
}

/** 根据路由 meta.roles 判断当前用户能否访问该页面 */
export function canAccessRoute(meta: RouteMeta, loginUser: API.LoginUserVO): boolean {
  const roles = meta.roles
  // 未配置 roles 的路由（如首页）登录即可访问
  if (!roles?.length) {
    return true
  }
  return hasRole(loginUser, ...roles)
}
