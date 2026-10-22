import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, apiSse, setPlanLimitHandler, setRequestPreflight } from './client'

afterEach(() => {
  vi.unstubAllGlobals()
  setPlanLimitHandler(null)
  setRequestPreflight(null)
})

describe('forbidden API responses', () => {
  it('uses an operation-neutral fallback for a non-JSON 403', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('Invalid CORS request', { status: 403 })))
    await expect(api('/api/v1/mock-interviews/drafts', { method: 'POST' })).rejects.toMatchObject({
      status: 403, category: 'FORBIDDEN', reason: 'FORBIDDEN', message: '没有权限执行此操作。',
    })
  })

  it('retains a structured server explanation for a denied operation', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json({
      ok: false, error: { category: 'FORBIDDEN', reason: 'CONSENT_REQUIRED', message: '请先确认本次资料授权范围。' },
    }, { status: 403 })))
    await expect(api('/api/v1/mock-interviews/sessions', { method: 'POST' })).rejects.toMatchObject({
      reason: 'CONSENT_REQUIRED', message: '请先确认本次资料授权范围。',
    })
  })
})

describe('plan-limit refusals', () => {
  it('reach the upgrade handler and still reject for the caller', async () => {
    const handler = vi.fn()
    setPlanLimitHandler(handler)
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json({
      ok: false, error: { category: 'CONFLICT', reason: 'AI_QUOTA_INSUFFICIENT', message: 'AI 次数不足' },
    }, { status: 409 })))
    await expect(api('/api/v1/job-matches', { method: 'POST' })).rejects.toMatchObject({ reason: 'AI_QUOTA_INSUFFICIENT' })
    expect(handler).toHaveBeenCalledWith(expect.objectContaining({ reason: 'AI_QUOTA_INSUFFICIENT' }))
  })

  it('leave other conflicts alone', async () => {
    const handler = vi.fn()
    setPlanLimitHandler(handler)
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json({
      ok: false, error: { category: 'CONFLICT', reason: 'VERSION_CONFLICT', message: '版本冲突' },
    }, { status: 409 })))
    await expect(api('/api/v1/job-applications/a1', { method: 'PUT' })).rejects.toMatchObject({ reason: 'VERSION_CONFLICT' })
    expect(handler).not.toHaveBeenCalled()
  })
})


describe('stream payment completion', () => {
  it('clears a fully received structured rejection and retains an in-flight request', async () => {
    const complete = vi.fn()
    setRequestPreflight(async () => ({ complete }))
    for (const reason of ['AI_QUOTE_CHANGED', 'AI_REQUEST_RUNNING', 'AI_REQUEST_IN_PROGRESS', 'AI_FOREGROUND_TASK_LIMIT', 'AI_BACKGROUND_TASK_LIMIT']) {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json({ ok: false, error: { reason, category: 'CONFLICT', message: reason } }, { status: 409 })))
      await expect(apiSse('/stream', { method: 'POST' }, vi.fn())).rejects.toMatchObject({ reason })
    }
    expect(complete.mock.calls).toEqual([[true], [false], [false], [false], [false]])
  })
  it('retains identity for an unreadable error response', async () => {
    const complete = vi.fn()
    setRequestPreflight(async () => ({ complete }))
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{', { status: 502 })))
    await expect(apiSse('/stream', { method: 'POST' }, vi.fn())).rejects.toThrow()
    expect(complete).not.toHaveBeenCalledWith(true)
  })
})
