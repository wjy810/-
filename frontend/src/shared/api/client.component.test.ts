import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from './client'

afterEach(() => vi.unstubAllGlobals())

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
