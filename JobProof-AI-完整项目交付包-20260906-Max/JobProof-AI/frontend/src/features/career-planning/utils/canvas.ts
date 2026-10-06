import type { CanvasNode, CanvasNodeStatus, CareerCanvas } from '../types'

export type CanvasTreeItem = {
  node: CanvasNode
  children: CanvasTreeItem[]
}

export type CanvasLayoutNode = {
  node: CanvasNode
  depth: number
  x: number
  y: number
}

export function parentMap(canvas: CareerCanvas): Map<string, string> {
  return new Map(canvas.relations
    .filter(relation => relation.type === 'TREE_PARENT')
    .map(relation => [relation.fromNodeId, relation.toNodeId]))
}

export function buildCanvasTree(canvas: CareerCanvas): CanvasTreeItem[] {
  const byId = new Map(canvas.nodes.map(node => [node.logicalNodeId, node]))
  const children = new Map<string, CanvasNode[]>()
  const parents = parentMap(canvas)
  for (const node of canvas.nodes) {
    const parentId = parents.get(node.logicalNodeId)
    if (!parentId || !byId.has(parentId)) continue
    const bucket = children.get(parentId) ?? []
    bucket.push(node)
    children.set(parentId, bucket)
  }
  const sort = (nodes: CanvasNode[]) => [...nodes].sort((a, b) => a.sortOrder - b.sortOrder || a.title.localeCompare(b.title, 'zh-CN'))
  const visit = (node: CanvasNode, path: Set<string>): CanvasTreeItem => {
    if (path.has(node.logicalNodeId)) return { node, children: [] }
    const nextPath = new Set(path).add(node.logicalNodeId)
    return { node, children: sort(children.get(node.logicalNodeId) ?? []).map(child => visit(child, nextPath)) }
  }
  const roots = canvas.nodes.filter(node => !parents.has(node.logicalNodeId) || node.type === 'CAREER')
  return sort(roots).map(node => visit(node, new Set()))
}

export function layoutCanvas(canvas: CareerCanvas): CanvasLayoutNode[] {
  const tree = buildCanvasTree(canvas)
  const depthById = new Map<string, number>()
  let maxDepth = 0
  const registerDepth = (item: CanvasTreeItem, depth: number): void => {
    depthById.set(item.node.logicalNodeId, depth)
    maxDepth = Math.max(maxDepth, depth)
    item.children.forEach(child => registerDepth(child, depth + 1))
  }
  tree.forEach(item => registerDepth(item, 0))

  let leafRow = 0
  const yById = new Map<string, number>()
  const assignY = (item: CanvasTreeItem): number => {
    const childY = item.children.map(assignY)
    const y = childY.length ? childY.reduce((sum, value) => sum + value, 0) / childY.length : 72 + leafRow++ * 92
    yById.set(item.node.logicalNodeId, y)
    return y
  }
  tree.forEach(assignY)
  return canvas.nodes.map(node => {
    const depth = depthById.get(node.logicalNodeId) ?? 0
    return { node, depth, x: 80 + (maxDepth - depth) * 300, y: yById.get(node.logicalNodeId) ?? 72 }
  })
}

export function visibleCanvasNodeIds(
  canvas: CareerCanvas,
  query: string,
  status: CanvasNodeStatus | 'ALL',
  focusNodeId?: string,
): Set<string> {
  const normalized = query.trim().toLocaleLowerCase('zh-CN')
  const parents = parentMap(canvas)
  const children = new Map<string, string[]>()
  for (const [child, parent] of parents) children.set(parent, [...(children.get(parent) ?? []), child])
  const matched = canvas.nodes.filter(node => {
    const queryMatches = !normalized || `${node.title} ${JSON.stringify(node.detail)}`.toLocaleLowerCase('zh-CN').includes(normalized)
    return queryMatches && (status === 'ALL' || node.status === status)
  }).map(node => node.logicalNodeId)
  const visible = new Set<string>()
  const addAncestors = (id: string): void => {
    let current: string | undefined = id
    while (current && !visible.has(current)) {
      visible.add(current)
      current = parents.get(current)
    }
  }
  const addDescendants = (id: string): void => {
    visible.add(id)
    for (const child of children.get(id) ?? []) if (!visible.has(child)) addDescendants(child)
  }
  if (focusNodeId) {
    addAncestors(focusNodeId)
    addDescendants(focusNodeId)
    for (const relation of canvas.relations.filter(item => item.type === 'PREREQUISITE')) {
      if (visible.has(relation.fromNodeId) || visible.has(relation.toNodeId)) {
        addAncestors(relation.fromNodeId)
        addAncestors(relation.toNodeId)
      }
    }
  } else {
    matched.forEach(addAncestors)
    if (!normalized && status === 'ALL') canvas.nodes.forEach(node => visible.add(node.logicalNodeId))
  }
  return visible
}

export function canvasStatusCounts(canvas: CareerCanvas): Record<CanvasNodeStatus, number> {
  const counts: Record<CanvasNodeStatus, number> = {
    NOT_STARTED: 0, PLANNED: 0, LEARNING: 0, PENDING_VALIDATION: 0, MASTERED: 0, PAUSED: 0,
  }
  canvas.nodes.forEach(node => { counts[node.status]++ })
  return counts
}

export type VirtualRange = { start: number; end: number; offsetTop: number; totalHeight: number }

export function virtualCanvasRange(
  total: number,
  scrollTop: number,
  viewportHeight: number,
  rowHeight = 63,
  overscan = 6,
): VirtualRange {
  const safeTotal = Math.max(0, Math.floor(total))
  const safeRowHeight = Math.max(1, rowHeight)
  const start = Math.max(0, Math.floor(Math.max(0, scrollTop) / safeRowHeight) - Math.max(0, overscan))
  const visibleCount = Math.ceil(Math.max(0, viewportHeight) / safeRowHeight) + Math.max(0, overscan) * 2
  const end = Math.min(safeTotal, Math.max(start, start + visibleCount))
  return { start, end, offsetTop: start * safeRowHeight, totalHeight: safeTotal * safeRowHeight }
}
