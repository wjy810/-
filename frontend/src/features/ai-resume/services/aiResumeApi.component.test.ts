import { afterEach, describe, expect, it, vi } from 'vitest'
import { generateAiResumeSummarySuggestions, cancelAiResumeResponse, streamAiResume } from './aiResumeApi'
const ok = (data: unknown) => new Response(JSON.stringify({ ok: true, data }), { status: 200 })
afterEach(() => vi.unstubAllGlobals())
describe('suggestion business identity', () => {
  it('reuses the actual business ID despite a caller creating a new ID after a lost body', async () => {
    let calls = 0
    const fetcher = vi.fn(async () => ++calls === 1 ? new Response('{', { status: 200 }) : ok({ candidates: [] }))
    vi.stubGlobal('fetch', fetcher)
    await expect(generateAiResumeSummarySuggestions('identity-conversation', 'summary', 'facts', 'first')).rejects.toThrow()
    await generateAiResumeSummarySuggestions('identity-conversation', 'summary', 'facts', 'retry')
    await generateAiResumeSummarySuggestions('identity-conversation', 'summary', 'facts', 'new-attempt')
    const bodies = (fetcher.mock.calls as unknown as [string, RequestInit][]).map(c => JSON.parse(c[1].body as string))
    expect(bodies.map(b => b.clientRequestId)).toEqual(['first', 'first', 'new-attempt'])
  })
  it('changes identity for changed input and cancels the retained original operation', async () => {
    const fetcher = vi.fn(async (path: string) => path.endsWith('/cancel') ? ok({ status: 'CANCELLED' }) : new Response('{', { status: 200 }))
    vi.stubGlobal('fetch', fetcher)
    await expect(generateAiResumeSummarySuggestions('cancel-conversation', 'summary', 'facts', 'original')).rejects.toThrow()
    await expect(generateAiResumeSummarySuggestions('cancel-conversation', 'summary', 'facts', 'retry-alias')).rejects.toThrow()
    await cancelAiResumeResponse('cancel-conversation', 'retry-alias')
    expect(fetcher.mock.calls[2]?.[0]).toContain('/messages/original/cancel')
    await expect(generateAiResumeSummarySuggestions('cancel-conversation', 'summary', 'changed', 'changed-id')).rejects.toThrow()
    const last = fetcher.mock.calls.at(-1) as unknown as [string, RequestInit]
    expect(JSON.parse(last[1].body as string).clientRequestId).toBe('changed-id')
  })
})


describe('chat business identity', () => {
  it('replays the original message after losing the stream before user.accepted', async () => {
    let count = 0
    const fetcher = vi.fn(async () => {
      if (++count === 1) throw new TypeError('connection lost after commit')
      return new Response('event: assistant.completed\ndata: {"message":{"id":"persisted"}}\n\n', { status: 200 })
    })
    vi.stubGlobal('fetch', fetcher)
    await expect(streamAiResume('chat-retry', 'same text', 'original', vi.fn())).rejects.toThrow()
    await streamAiResume('chat-retry', 'same text', 'retry', vi.fn())
    await streamAiResume('chat-retry', 'same text', 'new-intent', vi.fn())
    const bodies = (fetcher.mock.calls as unknown as [string, RequestInit][]).map(c => JSON.parse(c[1].body as string))
    expect(bodies.map(b => b.clientMessageId)).toEqual(['original', 'original', 'new-intent'])
  })
  it('retains an accepted interrupted stream and cancels by its original message ID', async () => {
    const fetcher = vi.fn(async (path: string) => path.endsWith('/cancel') ? ok({ accepted: true }) : new Response('event: user.accepted\ndata: {"message":{"id":"persisted-user"}}\n\n'))
    vi.stubGlobal('fetch', fetcher)
    await streamAiResume('chat-cancel', 'same text', 'original', vi.fn())
    await streamAiResume('chat-cancel', 'same text', 'retry-alias', vi.fn())
    await cancelAiResumeResponse('chat-cancel', 'retry-alias')
    expect(fetcher.mock.calls[2]?.[0]).toContain('/messages/original/cancel')
    await streamAiResume('chat-cancel', 'changed text', 'changed', vi.fn())
    const last = fetcher.mock.calls.at(-1) as unknown as [string, RequestInit]
    expect(JSON.parse(last[1].body as string).clientMessageId).toBe('changed')
  })
})
