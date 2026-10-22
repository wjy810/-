import { afterEach, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { setRequestPreflight } from '@/shared/api/client'
import { installAiPayments } from '@/features/billing/payment'
import { useConversation } from './useConversation'
import type { WorkbenchSession } from './useWorkbenchSession'
import type { DescriptionAssist } from './useDescriptionAssist'

afterEach(() => { vi.unstubAllGlobals(); setRequestPreflight(null) })

it('the actual send/retry flow reuses the committed message when the complete response is lost', async () => {
  const committed = new Map<string, string>()
  let calls = 0
  const fetcher = vi.fn(async (_url: string, init: RequestInit) => {
    const { clientMessageId } = JSON.parse(init.body as string)
    if (!committed.has(clientMessageId)) committed.set(clientMessageId, `answer-${committed.size + 1}`)
    if (++calls === 1) throw new TypeError('response lost after server commit')
    return new Response(`event: user.accepted\ndata: {"message":{"id":"user-${clientMessageId}"}}\n\nevent: assistant.completed\ndata: {"message":{"id":"${committed.get(clientMessageId)}"}}\n\n`)
  })
  vi.stubGlobal('fetch', fetcher)
  const session = {
    conversation: ref({ id: 'real-send-retry', aiAvailable: true, consent: { status: 'GRANTED' }, messages: [], changeSets: [] }),
    messagePending: ref(false), fail: vi.fn(), clearError: vi.fn(), requestChatScroll: vi.fn(),
    load: vi.fn().mockResolvedValue(undefined), notify: vi.fn(),
  } as unknown as WorkbenchSession
  const assist = { fieldGenerationPending: ref(false) } as DescriptionAssist
  const chat = useConversation(session, assist)
  chat.draft.value = 'same intended question'
  await chat.send()
  expect(chat.draft.value).toBe('same intended question')
  await chat.send()
  expect(committed.size).toBe(1)
  const ids = fetcher.mock.calls.map(call => JSON.parse(call[1].body as string).clientMessageId)
  expect(ids[1]).toBe(ids[0])
  chat.draft.value = 'same intended question'
  await chat.send()
  expect(committed.size).toBe(2)
})


it.each(['AI_REQUEST_RUNNING', 'AI_REQUEST_IN_PROGRESS', 'AI_FOREGROUND_TASK_LIMIT', 'AI_BACKGROUND_TASK_LIMIT'].flatMap(reason => ['http', 'sse'].map(transport => ({ reason, transport }))))(
  'retains the real chat identity and quote through $transport $reason while the first operation is unresolved',
  async ({ reason, transport }) => {
    installAiPayments()
    let quotes = 0, attempts = 0
    const operations = new Set<string>()
    const businessCalls: RequestInit[] = []
    const fetcher = vi.fn(async (url: string, init: RequestInit) => {
      if (url.endsWith('/quote')) {
        quotes++
        return Response.json({ ok: true, data: { id: `quote-${quotes}`, source: 'QUOTA', units: 1, total: 0, remainingQuota: 10 } })
      }
      businessCalls.push(init)
      const body = JSON.parse(init.body as string)
      operations.add(body.clientMessageId)
      if (++attempts === 1) throw new TypeError('first response lost while generation is still running')
      if (attempts === 2) {
        if (transport === 'http') return Response.json({ ok: false, error: { reason, category: 'CONFLICT', message: 'still running' } }, { status: 409 })
        return new Response(`event: user.accepted\ndata: {"message":{"id":"persisted-user"}}\n\nevent: request.failed\ndata: {"errorCode":"${reason}","message":"still running"}\n\n`)
      }
      return new Response('event: assistant.completed\ndata: {"message":{"id":"one-durable-reply"}}\n\n')
    })
    vi.stubGlobal('fetch', fetcher)
    const session = {
      conversation: ref({ id: `retry-${transport}-${reason}`, aiAvailable: true, consent: { status: 'GRANTED' }, messages: [], changeSets: [] }),
      messagePending: ref(false), fail: vi.fn(), clearError: vi.fn(), requestChatScroll: vi.fn(),
      load: vi.fn().mockResolvedValue(undefined), notify: vi.fn(),
    } as unknown as WorkbenchSession
    const chat = useConversation(session, { fieldGenerationPending: ref(false) } as DescriptionAssist)
    chat.draft.value = 'same unresolved user intent'
    await chat.send()
    expect(chat.draft.value).toBe('same unresolved user intent')
    await chat.send()
    expect(chat.draft.value).toBe('same unresolved user intent')
    await chat.send()
    expect(operations.size).toBe(1)
    expect(quotes).toBe(1)
    expect(new Set(businessCalls.map(init => new Headers(init.headers).get('X-Ai-Operation-Id'))).size).toBe(1)
    expect(businessCalls.map(init => new Headers(init.headers).get('X-Ai-Quote'))).toEqual(['quote-1', 'quote-1', 'quote-1'])
    expect(chat.draft.value).toBe('')
  },
)


it('keeps the business identity when a gateway returns an unreadable error body after commit', async () => {
  let attempts = 0
  const fetcher = vi.fn(async () => ++attempts === 1
    ? new Response('<html>gateway failed after commit</html>', { status: 502 })
    : new Response('event: assistant.completed\ndata: {"message":{"id":"durable"}}\n\n'))
  vi.stubGlobal('fetch', fetcher)
  const session = {
    conversation: ref({ id: 'gateway-body-retry', aiAvailable: true, consent: { status: 'GRANTED' }, messages: [], changeSets: [] }),
    messagePending: ref(false), fail: vi.fn(), clearError: vi.fn(), requestChatScroll: vi.fn(),
    load: vi.fn().mockResolvedValue(undefined), notify: vi.fn(),
  } as unknown as WorkbenchSession
  const chat = useConversation(session, { fieldGenerationPending: ref(false) } as DescriptionAssist)
  chat.draft.value = 'same committed request'
  await chat.send()
  await chat.send()
  const ids = (fetcher.mock.calls as unknown as [string, RequestInit][]).map(call => JSON.parse(call[1].body as string).clientMessageId)
  expect(ids[1]).toBe(ids[0])
})
