import { reactive } from 'vue'
import { api, newRequestId, setRequestPreflight } from '@/shared/api/client'
import { catalog, type Shop } from './services/commerceApi'

export type AiQuote = {
  id: string | null; operation: string; name: string; units: number; source: 'QUOTA' | 'WALLET' | 'PERSONAL'
  unitPrice: number; total: number; availableBalance: number; remainingQuota: number; expiresAt: string
  unavailable: string | null; personalChannelId: string | null
}
export const paymentState = reactive<{ current: { quote: AiQuote; shop: Shop | null } | null }>({ current: null })
let resolveDialog: ((confirmed: boolean) => void) | null = null
let queue: Promise<unknown> = Promise.resolve()
type Retry = { quote: AiQuote; operationId: string }
const retries = new Map<string, Retry>()
async function retryKey(path: string, body: string) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(`${path}\n${body}`))
  return `jobproof:ai-retry:${Array.from(new Uint8Array(digest), v => v.toString(16).padStart(2, '0')).join('')}`
}
function storedRetry(key: string): Retry | undefined {
  try { return JSON.parse(localStorage.getItem(key) ?? 'null') ?? undefined } catch { return undefined }
}

export function settlePayment(confirmed: boolean) {
  paymentState.current = null
  resolveDialog?.(confirmed)
  resolveDialog = null
}

export function isAiBusinessRequest(path: string, method: string): boolean {
  if (method !== 'POST') return false
  return /^\/api\/v1\/ai-resume\/conversations\/[^/]+\/(?:messages\/(?:respond|stream)|cards\/[^/]+\/(?:records\/\d+\/description-suggestion|summary-suggestions|skill-suggestions|certificate-suggestions|honor-suggestions|credential-recommendations)|branches\/[^/]+\/translate)$/.test(path)
    || /^\/api\/v1\/(?:resumes\/[^/]+|career-library\/records\/[^/]+)\/ai-candidates$/.test(path)
    || /^\/api\/v1\/job-matches\/[^/]+\/(?:analyze|resume-analysis)$/.test(path)
    || /^\/api\/v1\/career-planning\/sessions\/[^/]+\/(?:interviews(?:\/tasks)?|recommendations(?:\/tasks)?|canvas\/(?:generate|tasks|proposals(?:\/tasks)?)|validations(?:\/quiz)?|validation-batches)$/.test(path)
    || /^\/api\/v1\/tasks\/[^/]+\/retry$/.test(path)
    || /^\/api\/v1\/mock-interviews\/sessions(?:\/[^/]+\/(?:answers\/[^/]+|evaluate))?$/.test(path)
}

export function installAiPayments() {
  setRequestPreflight(async (path, init) => {
    if (!isAiBusinessRequest(path, (init.method ?? 'GET').toUpperCase())) return
    const body = typeof init.body === 'string' ? init.body : ''
    if (/(?:certificate|honor)-suggestions$/.test(path)) {
      try { if (String(JSON.parse(body).phase ?? 'NAMES').trim().toUpperCase() === 'NAMES' || !String(JSON.parse(body).phase ?? '').trim()) return } catch { /* server validates malformed input */ }
    }
    const key = await retryKey(path, body)
    let retry = retries.get(key) ?? storedRetry(key)
    let quote = retry?.quote
    if (!quote) {
      quote = await api<AiQuote | null>('/api/v1/ai/charges/quote', { method: 'POST', body: JSON.stringify({ path, body }), signal: init.signal }) ?? undefined
      if (!quote) return
      if (quote.source === 'WALLET' || quote.unavailable) {
        const selected = quote
        const ask = async () => {
          if (init.signal?.aborted) throw new DOMException('用户取消', 'AbortError')
          const data = await catalog().catch(() => null)
          paymentState.current = { quote: selected, shop: data?.shop ?? null }
          const onAbort = () => settlePayment(false)
          let confirmed: boolean
          try {
            confirmed = await new Promise<boolean>(resolve => {
              resolveDialog = resolve
              init.signal?.addEventListener('abort', onAbort, { once: true })
            })
          } finally { init.signal?.removeEventListener('abort', onAbort) }
          if (!confirmed || selected.unavailable) throw new DOMException('已取消余额调用', 'AbortError')
        }
        const awaiting = queue.then(ask, ask)
        queue = awaiting.catch(() => undefined)
        await awaiting
      }
      if (!quote.id) throw new DOMException('当前不可使用余额调用', 'AbortError')
      retry = { quote, operationId: newRequestId() }
      retries.set(key, retry)
      try { localStorage.setItem(key, JSON.stringify(retry)) } catch { /* memory still supports immediate retry */ }
    }
    return {
      headers: { 'X-Ai-Operation-Id': retry!.operationId, 'X-Ai-Quote': quote.id!, 'X-Ai-Payment-Confirmed': String(quote.source === 'WALLET') },
      complete(receivedResponse: boolean) { if (receivedResponse) { retries.delete(key); try { localStorage.removeItem(key) } catch { /* unavailable storage */ } } },
    }
  })
}
