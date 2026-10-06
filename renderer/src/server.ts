/**
 * JobProof resume renderer (docs/phase2/03 §5.3).
 *   POST /v1/render  { format, payload, options } → PDF / PNG with X-Page-Count, X-Overflow-Mm, X-Render-Ms
 *   GET  /healthz    → status and counters (always 200 while the process runs)
 *   GET  /readyz     → 200 once the browser and print pages are warm
 */
import { timingSafeEqual } from 'node:crypto'
import { createServer, type IncomingMessage, type ServerResponse } from 'node:http'
import { loadConfig, type RendererConfig } from './config.ts'
import { log } from './log.ts'
import { BusyError, PagePool } from './pool.ts'
import { renderOn, TemplateUnknownError } from './render.ts'
import { startStaticServer } from './static.ts'
import { InvalidRequest, parseRenderRequest } from './validate.ts'

export const RENDERER_VERSION = 'resume-render-v4'

function send(response: ServerResponse, status: number, body: unknown, headers: Record<string, string> = {}): void {
  const text = JSON.stringify(body)
  response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(text), ...headers }).end(text)
}

function authorized(request: IncomingMessage, token: string): boolean {
  const header = request.headers.authorization ?? ''
  const given = Buffer.from(header.startsWith('Bearer ') ? header.slice(7) : '')
  const expected = Buffer.from(token)
  return given.length === expected.length && timingSafeEqual(given, expected)
}

async function readBody(request: IncomingMessage, limit: number): Promise<string> {
  let size = 0
  const chunks: Buffer[] = []
  for await (const chunk of request) {
    size += (chunk as Buffer).length
    if (size > limit) throw new InvalidRequest('PAYLOAD_TOO_LARGE', 'request body too large')
    chunks.push(chunk as Buffer)
  }
  return Buffer.concat(chunks).toString('utf8')
}

export async function startServer(config: RendererConfig) {
  const assets = await startStaticServer(config.printDir)
  const pool = new PagePool(config, assets.origin)
  void pool.start().catch(error => log('error', 'renderer failed to start', { error: String(error) }))

  const server = createServer(async (request, response) => {
    const requestId = String(request.headers['x-request-id'] ?? '').slice(0, 64) || undefined
    const url = new URL(request.url ?? '/', 'http://renderer')
    if (request.method === 'GET' && url.pathname === '/healthz') {
      send(response, 200, { ok: true, rendererVersion: RENDERER_VERSION, chromium: pool.chromiumVersion, pool: { size: pool.size, busy: pool.busy, queued: pool.queued }, renders: pool.renders, failures: pool.errors })
      return
    }
    if (request.method === 'GET' && url.pathname === '/readyz') {
      send(response, pool.ready ? 200 : 503, { ready: pool.ready })
      return
    }
    if (request.method !== 'POST' || url.pathname !== '/v1/render') {
      send(response, 404, { code: 'NOT_FOUND' })
      return
    }
    if (!authorized(request, config.token)) {
      send(response, 401, { code: 'UNAUTHORIZED' })
      return
    }
    const started = Date.now()
    let slot: Awaited<ReturnType<PagePool['acquire']>> | null = null
    let failed = false
    try {
      const parsed = parseRenderRequest(JSON.parse(await readBody(request, config.maxBodyBytes)))
      slot = await pool.acquire()
      const output = await renderOn(slot.page, parsed, config.renderTimeoutMs)
      const ms = Date.now() - started
      response.writeHead(200, {
        'Content-Type': output.contentType,
        'Content-Length': output.body.length,
        'X-Page-Count': String(output.pageCount),
        'X-Overflow-Mm': String(output.overflowMm),
        'X-Overflow-Section': output.firstOverflowSection ?? '',
        'X-Render-Ms': String(ms),
        'X-Renderer-Version': RENDERER_VERSION,
      }).end(output.body)
      log('info', 'rendered', { requestId, format: parsed.format, templateId: parsed.payload.templateId, pages: output.pageCount, ms })
    } catch (error) {
      if (error instanceof InvalidRequest) send(response, error.code === 'PAYLOAD_TOO_LARGE' ? 413 : 400, { code: error.code, message: error.message })
      else if (error instanceof SyntaxError) send(response, 400, { code: 'INVALID_PAYLOAD', message: 'body is not valid JSON' })
      else if (error instanceof TemplateUnknownError) send(response, 422, { code: 'TEMPLATE_UNKNOWN', message: error.message })
      else if (error instanceof BusyError) send(response, 503, { code: 'BUSY', message: error.message }, { 'Retry-After': '2' })
      else {
        failed = true
        log('error', 'render failed', { requestId, error: String(error) })
        send(response, 500, { code: 'RENDER_FAILED', message: 'render failed' })
      }
    } finally {
      if (slot) await pool.release(slot, failed)
    }
  })

  await new Promise<void>(resolve => server.listen(config.port, config.host, resolve))
  log('info', 'renderer listening', { port: config.port, printDir: config.printDir })
  return {
    server,
    pool,
    async close() {
      await new Promise<void>(resolve => server.close(() => resolve()))
      await pool.close()
      await assets.close()
    },
  }
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const config = loadConfig()
  const running = await startServer(config)
  for (const signal of ['SIGINT', 'SIGTERM'] as const) {
    process.on(signal, () => { void running.close().then(() => process.exit(0)) })
  }
}
