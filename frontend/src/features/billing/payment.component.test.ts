import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, apiSse, setRequestPreflight } from '@/shared/api/client'
import { installAiPayments, isAiBusinessRequest, paymentState, settlePayment } from './payment'

const path = '/api/v1/ai-resume/conversations/id/cards/summary/summary-suggestions'
const quote = (source = 'WALLET') => ({ id: crypto.randomUUID(), name: '总结建议', source, total: 2, unitPrice: 2, units: 1, remainingQuota: 0, availableBalance: 20, expiresAt: new Date(Date.now() + 300000).toISOString() })
const response = (data: unknown) => new Response(JSON.stringify({ ok: true, data }), { status: 200, headers: { 'Content-Type': 'application/json' } })
describe('AI payment authorization', () => {
  beforeEach(() => installAiPayments())
  afterEach(() => { settlePayment(false); setRequestPreflight(null); vi.unstubAllGlobals() })
  it('leaves non-AI writes and report aggregation outside billing', () => {
    expect(isAiBusinessRequest('/api/v1/mock-interviews/sessions/id/complete', 'POST')).toBe(false)
    expect(isAiBusinessRequest('/api/v1/ai-resume/conversations/id/messages', 'POST')).toBe(false)
    expect(isAiBusinessRequest('/api/v1/mock-interviews/sessions/id/evaluate', 'POST')).toBe(true)
    expect(isAiBusinessRequest(path, 'GET')).toBe(false)
  })
  it('does not dispatch a business request if payment is cancelled', async () => {
    const fetcher = vi.fn(async (url: string) => response(url.endsWith('/quote') ? quote() : { shop: null }))
    vi.stubGlobal('fetch', fetcher)
    const pending = api(path, { method: 'POST', body: '{"requestId":"cancel"}' })
    const rejected = expect(pending).rejects.toMatchObject({ name: 'AbortError' })
    await vi.waitFor(() => expect(paymentState.current).not.toBeNull())
    expect(fetcher.mock.calls.some(call => call[0] === path)).toBe(false)
    settlePayment(false)
    await rejected
    expect(fetcher.mock.calls.some(call => call[0] === path)).toBe(false)
  })
  it('uses server quote and explicit authorization only after confirmation', async () => {
    const price = quote()
    const fetcher = vi.fn(async (url: string) => response(url.endsWith('/quote') ? price : url.endsWith('/catalog') ? { shop: null } : { saved: true }))
    vi.stubGlobal('fetch', fetcher)
    const pending = api(path, { method: 'POST', body: '{"requestId":"confirmed"}' })
    await vi.waitFor(() => expect(paymentState.current).not.toBeNull())
    settlePayment(true); await pending
    const call = (fetcher.mock.calls as unknown as [string, RequestInit][]).find(c => c[0] === path)!
    expect(new Headers(call[1].headers).get('X-Ai-Quote')).toBe(price.id)
    expect(new Headers(call[1].headers).get('X-Ai-Payment-Confirmed')).toBe('true')
  })
  it('quota requests proceed without authorizing a balance charge', async () => {
    const fetcher = vi.fn(async (url: string) => response(url.endsWith('/quote') ? quote('QUOTA') : { saved: true }))
    vi.stubGlobal('fetch', fetcher)
    await api(path, { method: 'POST', body: '{"requestId":"quota"}' })
    const call = (fetcher.mock.calls as unknown as [string, RequestInit][]).find(c => c[0] === path)!
    expect(new Headers(call[1].headers).get('X-Ai-Payment-Confirmed')).toBe('false')
    expect(paymentState.current).toBeNull()
  })
  it('keeps the same payment quote after a network failure', async () => {
    let attempts=0; const price=quote()
    const fetcher=vi.fn(async (url: string) => {
      if(url.endsWith('/quote')) return response(price)
      if(url.endsWith('/catalog')) return response({shop:null})
      if(++attempts===1) throw new TypeError('network')
      return response({saved:true})
    })
    vi.stubGlobal('fetch',fetcher)
    const init={method:'POST',body:'{"requestId":"retry"}'}
    const pending=api(path,init); const rejected=expect(pending).rejects.toThrow('network')
    await vi.waitFor(()=>expect(paymentState.current).not.toBeNull());settlePayment(true);await rejected
    await api(path,init)
    expect(fetcher.mock.calls.filter(c=>c[0].endsWith('/quote'))).toHaveLength(1)
    const calls=(fetcher.mock.calls as unknown as [string,RequestInit][]).filter(c=>c[0]===path)
    expect(calls.map(c=>new Headers(c[1].headers).get('X-Ai-Quote'))).toEqual([price.id,price.id])
  })
  it('retains authorization after headers with a broken body, even after quote expiry', async () => {
    const price = { ...quote('QUOTA'), expiresAt: new Date(0).toISOString() }
    let business = 0
    const fetcher = vi.fn(async (url: string) => {
      if (url.endsWith('/quote')) return response(price)
      if (++business === 1) return new Response('{"ok":true,"data":', { status: 200 })
      return response({ saved: true })
    })
    vi.stubGlobal('fetch', fetcher)
    const init = { method: 'POST', body: '{"requestId":"broken-body"}' }
    await expect(api(path, init)).rejects.toThrow()
    await api(path, init)
    expect(fetcher.mock.calls.filter(c => c[0].endsWith('/quote'))).toHaveLength(1)
    const calls = (fetcher.mock.calls as unknown as [string, RequestInit][]).filter(c => c[0] === path)
    const ids = calls.map(c => new Headers(c[1].headers).get('X-Ai-Operation-Id'))
    expect(ids[0]).toBeTruthy(); expect(ids[1]).toBe(ids[0])
  })
  it('refreshes a fully rejected expired SSE quote and asks for wallet consent again', async () => {
    const streamPath = '/api/v1/ai-resume/conversations/expired/messages/stream'
    const oldQuote = quote(), newQuote = quote()
    let quotes = 0, business = 0
    const fetcher = vi.fn(async (url: string) => {
      if (url.endsWith('/quote')) return response(++quotes === 1 ? oldQuote : newQuote)
      if (url.endsWith('/catalog')) return response({ shop: null })
      if (++business === 1) return Response.json({ ok: false, error: { reason: 'AI_QUOTE_CHANGED', category: 'CONFLICT', message: 'expired' } }, { status: 409 })
      return new Response('event: assistant.completed\ndata: {}\n\n')
    })
    vi.stubGlobal('fetch', fetcher)
    const init = { method: 'POST', body: '{"clientMessageId":"stable","text":"question"}' }
    const first = apiSse(streamPath, init, vi.fn())
    const rejected = expect(first).rejects.toMatchObject({ reason: 'AI_QUOTE_CHANGED' })
    await vi.waitFor(() => expect(paymentState.current?.quote.id).toBe(oldQuote.id))
    settlePayment(true); await rejected
    const retry = apiSse(streamPath, init, vi.fn())
    await vi.waitFor(() => expect(paymentState.current?.quote.id).toBe(newQuote.id))
    expect(business).toBe(1)
    settlePayment(true); await retry
    expect(quotes).toBe(2)
    expect(business).toBe(2)
  })
  it('does not quote deterministic certificate and honor name extraction', async () => {
    const fetcher = vi.fn(async () => response({ names: [] }))
    vi.stubGlobal('fetch', fetcher)
    for (const kind of ['certificate', 'honor']) {
      await api(`/api/v1/ai-resume/conversations/id/cards/id/${kind}-suggestions`, { method: 'POST', body: '{"phase":" names "}' })
    }
    expect(fetcher).toHaveBeenCalledTimes(2)
  })
})
