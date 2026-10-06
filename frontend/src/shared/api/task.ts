import { api } from './client'
import { isApiClientError } from './types'

export type TaskStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'

export type TaskView = {
  id: string
  accountId: string
  taskType: string
  status: TaskStatus | string
  inputVersion?: string | null
  resultVersion?: string | null
  failureReason?: string | null
  progressPercent?: number | null
  checkpointCode?: string | null
  errorCode?: string | null
  createdAt?: string | null
  updatedAt?: string | null
  fileId?: string | null
  downloadAvailable?: boolean | null
  downloadUrl?: string | null
  downloadExpired?: boolean | null
  downloadExpiresAt?: string | null
  /** Resume exports: page count and the ATS text check of the produced PDF. */
  result?: { pageCount?: number; atsCheck?: AtsTextCheck } | null
}

export type AtsTextCheck = {
  passed: boolean
  pageCount: number
  textLength: number
  checks: Array<{ code: string; passed: boolean; detail?: string | null }>
}

export function fetchTask(id: string): Promise<TaskView> {
  return api<TaskView>(`/api/v1/tasks/${id}`)
}

export function cancelTask(id: string): Promise<TaskView> {
  return api<TaskView>(`/api/v1/tasks/${id}/cancel`, { method: 'POST' })
}

export function retryTask(id: string): Promise<TaskView> {
  return api<TaskView>(`/api/v1/tasks/${id}/retry`, { method: 'POST' })
}

export function isCancellableTask(status?: string | null): boolean {
  return status === 'PENDING' || status === 'RUNNING'
}

export function isTaskNotCancellable(error: unknown): boolean {
  return isApiClientError(error) && error.reason === 'TASK_NOT_CANCELLABLE'
}

export function taskNotCancellableMessage(error: unknown): string {
  const detail =
    isApiClientError(error) && error.message?.trim()
      ? error.message.trim()
      : '成功、失败或已取消任务不可再取消，请开新任务'
  return `${detail}（409 TASK_NOT_CANCELLABLE。这不是系统故障。）`
}

export function isManualRetryTask(status?: string | null): boolean {
  return status === 'FAILED' || status === 'CANCELLED'
}

export function isOpenTask(status?: string | null): boolean {
  return status === 'PENDING' || status === 'RUNNING'
}

export function taskStatusLabel(status?: string | null): string {
  if (!status) return '—'
  return ({
    PENDING: '排队',
    RUNNING: '执行中',
    SUCCEEDED: '已成功',
    FAILED: '失败',
    CANCELLED: '已取消',
  } as Record<string, string>)[status] ?? status
}

export function isJdParseTask(taskType?: string | null): boolean {
  return taskType === 'JD_PARSE'
}

export function isStructuralTaskFailure(reason?: string | null, message?: string | null): boolean {
  const text = `${reason ?? ''} ${message ?? ''}`
  return /JD_TEXT_TOO_SHORT|JD_TEXT_TOO_LONG|JD_NOT_JOB_DESCRIPTION|不是岗位描述|不足 80|超过 20000|结构校验|内容安全/.test(
    text,
  )
}
