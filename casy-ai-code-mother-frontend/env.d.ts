/**
 * env.d.ts 是 TypeScript 类型声明文件，不参与运行，只给 TS 编译器用。
 *
 * 你这个项目里它做了三件事：
 *
 * /// <reference types="vite/client" />
 * 引入 Vite 内置类型，让 TS 认识：
 *
 * import.meta.env
 * import xxx from './App.vue'
 * 图片/CSS 等静态资源 import
 * ImportMetaEnv（上次加的）
 * 给 .env 里的 VITE_API_BASE_URL 补类型，否则写 import.meta.env.VITE_API_BASE_URL 可能报 TS 错。
 *
 * declare module 'vue-router'
 * 扩展路由 meta 类型，让 meta.roles、hideFooter 等有提示。
 */
/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

import 'vue-router'

// 扩展 vue-router 的 RouteMeta，使 meta.roles 有类型提示
declare module 'vue-router' {
  interface RouteMeta {
    roles?: string[]
    hideFooter?: boolean
    noPadding?: boolean
  }
}
