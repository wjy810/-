export type ScrollMetrics = {
  scrollTop: number
  scrollHeight: number
  clientHeight: number
}

export type ScrollEdges = {
  canScrollUp: boolean
  canScrollDown: boolean
  bottomDistance: number
}

export function scrollEdges(metrics: ScrollMetrics, epsilon = 1): ScrollEdges {
  const maxScrollTop = Math.max(0, metrics.scrollHeight - metrics.clientHeight)
  const scrollTop = Math.min(maxScrollTop, Math.max(0, metrics.scrollTop))
  const bottomDistance = Math.max(0, maxScrollTop - scrollTop)
  return {
    canScrollUp: scrollTop > epsilon,
    canScrollDown: bottomDistance > epsilon,
    bottomDistance,
  }
}

export function nextAutoFollowState(
  current: boolean,
  previousScrollTop: number,
  metrics: ScrollMetrics,
  nearBottomThreshold = 48,
): boolean {
  const edges = scrollEdges(metrics)
  if (metrics.scrollTop < previousScrollTop - 1) return false
  if (edges.bottomDistance <= nearBottomThreshold) return true
  return current
}

export function shouldShowReturnToBottom(metrics: ScrollMetrics, threshold = 240): boolean {
  return scrollEdges(metrics).bottomDistance > threshold
}
