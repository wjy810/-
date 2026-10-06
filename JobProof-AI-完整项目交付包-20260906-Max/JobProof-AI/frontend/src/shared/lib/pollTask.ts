import { fetchTask, isOpenTask, type TaskView } from '@/shared/api/task'

export async function pollTask(
  taskId: string,
  onTick: (task: TaskView) => void,
  signal?: AbortSignal,
  intervalMs = 1400,
  maxWaitMs = 180_000,
): Promise<TaskView> {
  const started = Date.now()
  let current = await fetchTask(taskId)
  onTick(current)
  while (isOpenTask(current.status)) {
    if (signal?.aborted) {
      throw new DOMException('轮询已取消', 'AbortError')
    }
    if (Date.now() - started > maxWaitMs) {
      throw new Error('任务仍在进行，已停止自动刷新。请稍后点重新读取，不会自动重试。')
    }
    await wait(intervalMs, signal)
    current = await fetchTask(taskId)
    onTick(current)
  }
  return current
}

function wait(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('轮询已取消', 'AbortError'))
      return
    }
    const timer = window.setTimeout(() => {
      signal?.removeEventListener('abort', onAbort)
      resolve()
    }, ms)
    const onAbort = (): void => {
      window.clearTimeout(timer)
      reject(new DOMException('轮询已取消', 'AbortError'))
    }
    signal?.addEventListener('abort', onAbort, { once: true })
  })
}

export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}
