type Relation = { id: string; type: string; fromNodeId: string; toNodeId: string }
type NodeStatus = { logicalNodeId: string; status: string }

// Relations retain the existing child -> parent storage direction; timings run parent -> child.
export function branchGrowth(previous: ReadonlySet<string>, current: ReadonlySet<string>, relations: readonly Relation[]) {
  const added = new Set([...current].filter(id => !previous.has(id)))
  const parents = new Map(relations.filter(item => item.type === 'TREE_PARENT').map(item => [item.fromNodeId, item.toNodeId]))
  const depths = new Map<string, number>()
  for (const id of added) {
    let parent = parents.get(id)
    let depth = 0
    const visited = new Set([id])
    while (parent && added.has(parent) && !visited.has(parent)) {
      visited.add(parent); depth++; parent = parents.get(parent)
    }
    depths.set(id, depth)
  }
  const maximum = Math.max(0, ...depths.values())
  const delays = new Map([...depths].map(([id, depth]) => [id, maximum ? Math.round(depth / maximum * 280) : 0]))
  return { nodes: delays, edges: new Map(relations.filter(item => item.type === 'TREE_PARENT' && added.has(item.fromNodeId) && current.has(item.toNodeId)).map(item => [item.id, delays.get(item.fromNodeId) ?? 0])) }
}

export function confirmedMastery(previous: readonly NodeStatus[], current: readonly NodeStatus[]) {
  const before = new Map(previous.map(node => [node.logicalNodeId, node.status]))
  return new Set(current.filter(node => before.has(node.logicalNodeId) && before.get(node.logicalNodeId) !== 'MASTERED' && node.status === 'MASTERED').map(node => node.logicalNodeId))
}
