import type { Page } from 'playwright-core'
import type { RenderRequest } from './validate.ts'

export interface RenderOutput {
  body: Buffer
  contentType: string
  pageCount: number
  overflowMm: number
  firstOverflowSection: string | null
}

export class TemplateUnknownError extends Error {}

interface PrintResult { pageCount: number; pageLimit: number; overflowMm: number; firstOverflowSection: string | null; paperSize: string }

export async function renderOn(page: Page, request: RenderRequest, timeoutMs: number): Promise<RenderOutput> {
  const result = await withTimeout(page.evaluate(async (payload) => {
    const render = (window as unknown as { __JP_RENDER__: (p: unknown) => Promise<unknown> }).__JP_RENDER__
    try {
      return { ok: true, value: await render(payload) }
    } catch (error) {
      return { ok: false, message: error instanceof Error ? error.message : String(error) }
    }
  }, request.payload), timeoutMs, 'render timed out') as { ok: true; value: PrintResult } | { ok: false; message: string }
  if (!result.ok) {
    if (/Unknown resume template/.test(result.message)) throw new TemplateUnknownError(result.message)
    throw new Error(result.message)
  }
  const info = result.value
  if (request.format === 'png') {
    const first = page.locator('.rr-pages > .rr-page').first()
    const body = await withTimeout(first.screenshot({ type: 'png', scale: 'device', animations: 'disabled' }), timeoutMs, 'screenshot timed out')
    return { body, contentType: 'image/png', pageCount: info.pageCount, overflowMm: info.overflowMm, firstOverflowSection: info.firstOverflowSection }
  }
  const body = await withTimeout(page.pdf({ preferCSSPageSize: true, printBackground: true, tagged: true, outline: false }), timeoutMs, 'pdf timed out')
  return { body, contentType: 'application/pdf', pageCount: info.pageCount, overflowMm: info.overflowMm, firstOverflowSection: info.firstOverflowSection }
}

function withTimeout<T>(promise: Promise<T>, ms: number, message: string): Promise<T> {
  let timer: ReturnType<typeof setTimeout>
  return Promise.race([
    promise.finally(() => clearTimeout(timer)),
    new Promise<T>((_, reject) => { timer = setTimeout(() => reject(new Error(message)), ms) }),
  ])
}
