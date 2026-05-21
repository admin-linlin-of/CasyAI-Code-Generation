/// <reference types="vite/client" />

import 'vue-router'

// 扩展 vue-router 的 RouteMeta，使 meta.roles 有类型提示
declare module 'vue-router' {
  interface RouteMeta {
    roles?: string[]
  }
}
