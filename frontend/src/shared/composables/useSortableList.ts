/**
 * Drag-to-reorder for lists rendered from external state. Sortable's DOM move is reverted and
 * reported as `(from, to)`, so Vue stays the only writer of the DOM.
 */
import type { Ref } from 'vue'
import { useDraggable } from 'vue-draggable-plus'

export function useSortableList(element: Ref<HTMLElement | null>, options: {
  handle?: string
  onMove: (from: number, to: number) => void
  disabled?: () => boolean
  /** Class put on the list while dragging (synchronously, so the drag clone already sees it). */
  sortingClass?: string
}) {
  const sortingClass = options.sortingClass ?? 'is-sorting'
  return useDraggable(element, {
    handle: options.handle,
    animation: 180,
    ghostClass: 'is-drag-ghost',
    chosenClass: 'is-drag-chosen',
    dragClass: 'is-dragging',
    // Pointer-based dragging: consistent for <fieldset> items and touch; the clone lives on <body>.
    forceFallback: true,
    fallbackOnBody: true,
    fallbackTolerance: 4,
    filter: 'input, textarea, select, [contenteditable]',
    preventOnFilter: false,
    // Without a list ref vue-draggable-plus registers none of its own handlers (customUpdate
    // included), so the revert-and-report happens in our onUpdate.
    onStart: () => element.value?.classList.add(sortingClass),
    onEnd: () => element.value?.classList.remove(sortingClass),
    onUpdate: (event) => {
      const { from, item, oldIndex, newIndex, oldDraggableIndex, newDraggableIndex } = event
      // Put the node back where Vue rendered it; the re-render applies the new order.
      item.remove()
      from.insertBefore(item, from.children[oldIndex ?? 0] ?? null)
      if (options.disabled?.()) return
      const fromIndex = oldDraggableIndex ?? oldIndex
      const toIndex = newDraggableIndex ?? newIndex
      if (fromIndex != null && toIndex != null && fromIndex !== toIndex) options.onMove(fromIndex, toIndex)
    },
  })
}
