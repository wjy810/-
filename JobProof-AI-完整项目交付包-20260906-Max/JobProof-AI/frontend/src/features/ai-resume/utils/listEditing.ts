export function moveListItem<T>(items: readonly T[], index: number, direction: -1 | 1): T[] {
  const target = index + direction
  if (index < 0 || index >= items.length || target < 0 || target >= items.length) return [...items]
  const moved = [...items]
  const [item] = moved.splice(index, 1)
  if (item === undefined) return [...items]
  moved.splice(target, 0, item)
  return moved
}
