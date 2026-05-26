/** 圆形扩散动画的圆心坐标（一般为鼠标点击位置） */
export type ThemeTransitionPoint = {
  x: number
  y: number
}

/**
 * 在切换主题时包裹 DOM 更新，实现「从点击处向外扩散」的过渡动画。
 *
 * 原理（View Transition API）：
 * 1. startViewTransition 会在更新 DOM 前截取旧页面快照，更新后再截取新页面快照
 * 2. 浏览器自动生成 ::view-transition-old(root) 和 ::view-transition-new(root) 两个伪元素层
 * 3. 我们对 new 层做 clip-path 圆形裁剪动画，视觉上就像新主题从点击点扩散开
 *
 * @param update 真正切换主题的回调（改 data-theme、localStorage 等）
 * @param point  圆心坐标；不传则直接切换，不做动画（兼容不支持 API 的浏览器）
 */
export const withThemeTransition = (update: () => void, point?: ThemeTransitionPoint) => {
  // SSR 或无 document 时无法操作 DOM
  if (typeof document === 'undefined') {
    update()
    return
  }

  const startViewTransition = document.startViewTransition?.bind(document)
  // 浏览器不支持 View Transition，或未传入点击坐标时，退化为立即切换
  if (!startViewTransition || !point) {
    update()
    return
  }

  const { x, y } = point
  // 圆心到视口四边最远角的距离 = 扩散圆需要覆盖全屏的半径
  const radius = Math.hypot(
    Math.max(x, window.innerWidth - x),
    Math.max(y, window.innerHeight - y),
  )

  // 在 transition 回调里执行 update，浏览器才会正确捕获切换前后的两帧画面
  const transition = startViewTransition(update)

  transition.ready
    .then(() => {
      // 对新主题快照（::view-transition-new(root)）做圆形 clip-path 动画
      document.documentElement.animate(
        {
          clipPath: [
            `circle(0px at ${x}px ${y}px)`,
            `circle(${radius}px at ${x}px ${y}px)`,
          ],
        },
        {
          duration: 500,
          easing: 'ease-in-out',
          // 动画作用在 View Transition 的「新页面」伪元素上，而不是真实 DOM
          pseudoElement: '::view-transition-new(root)',
        },
      )
    })
    .catch(() => {})
}
