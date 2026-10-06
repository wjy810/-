/**
 * One Chromium, a fixed set of warm pages each holding print.html (docs/phase2/03 §5.4).
 * Pages only reach the loopback static origin; everything else is aborted.
 */
import { chromium, type Browser, type Page } from 'playwright-core'
import type { RendererConfig } from './config.ts'
import { log } from './log.ts'

export class BusyError extends Error {}

interface Slot { page: Page; renders: number; busy: boolean }

export class PagePool {
  private browser: Browser | null = null
  private slots: Slot[] = []
  private waiters: Array<(slot: Slot) => void> = []
  private failures = 0
  private starting: Promise<void> | null = null
  private closing = false
  renders = 0
  errors = 0

  private readonly config: RendererConfig
  private readonly origin: string

  constructor(config: RendererConfig, origin: string) {
    this.config = config
    this.origin = origin
  }

  get size() { return this.slots.length }
  get busy() { return this.slots.filter(slot => slot.busy).length }
  get queued() { return this.waiters.length }
  get ready() { return this.browser?.isConnected() === true && this.slots.length === this.config.poolSize }
  get chromiumVersion() { return this.browser?.version() ?? null }

  async start(): Promise<void> {
    if (!this.starting) this.starting = this.launch().finally(() => { this.starting = null })
    return this.starting
  }

  private async launch(): Promise<void> {
    await this.browser?.close().catch(() => undefined)
    this.slots = []
    this.browser = await chromium.launch({
      executablePath: this.config.chromiumPath,
      args: ['--disable-dev-shm-usage', '--font-render-hinting=none', '--disable-gpu', '--no-first-run'],
    })
    this.browser.on('disconnected', () => {
      if (this.closing) return
      log('warn', 'browser disconnected, restarting')
      void this.start()
    })
    for (let index = 0; index < this.config.poolSize; index += 1) this.slots.push({ page: await this.openPage(), renders: 0, busy: false })
    this.failures = 0
    log('info', 'renderer pool ready', { pool: this.config.poolSize, chromium: this.browser.version() })
    this.drain()
  }

  private async openPage(): Promise<Page> {
    const page = await this.browser!.newPage({ viewport: { width: 1200, height: 1600 }, deviceScaleFactor: 1 })
    await page.route('**/*', (route) => {
      const url = route.request().url()
      if (url.startsWith(`${this.origin}/`) || url.startsWith('data:') || url.startsWith('blob:')) return route.continue()
      return route.abort('blockedbyclient')
    })
    page.on('pageerror', error => log('warn', 'print page error', { error: error.message }))
    await page.goto(`${this.origin}/print.html`, { waitUntil: 'load', timeout: 30_000 })
    await page.waitForFunction(() => (window as unknown as { __JP_READY__?: boolean }).__JP_READY__ === true, null, { timeout: 30_000 })
    return page
  }

  async acquire(): Promise<Slot> {
    if (!this.browser?.isConnected()) await this.start()
    const free = this.slots.find(slot => !slot.busy)
    if (free) {
      free.busy = true
      return free
    }
    return new Promise<Slot>((resolve, reject) => {
      const timer = setTimeout(() => {
        this.waiters = this.waiters.filter(waiter => waiter !== take)
        reject(new BusyError('render queue timed out'))
      }, this.config.queueTimeoutMs)
      const take = (slot: Slot) => {
        clearTimeout(timer)
        slot.busy = true
        resolve(slot)
      }
      this.waiters.push(take)
    })
  }

  /** Returns a page to the pool; a failed or worn-out page is replaced first. */
  async release(slot: Slot, failed: boolean): Promise<void> {
    slot.renders += 1
    this.renders += 1
    if (failed) {
      this.errors += 1
      this.failures += 1
    } else {
      this.failures = 0
    }
    if (this.failures >= 3) {
      log('error', 'three consecutive render failures, restarting browser')
      await this.start()
      return
    }
    if (failed || slot.renders >= this.config.recyclePageAfter || slot.page.isClosed()) {
      await slot.page.close().catch(() => undefined)
      try {
        slot.page = await this.openPage()
        slot.renders = 0
      } catch (error) {
        log('error', 'could not reopen print page', { error: String(error) })
        await this.start()
        return
      }
    }
    slot.busy = false
    this.drain()
  }

  private drain(): void {
    while (this.waiters.length) {
      const free = this.slots.find(slot => !slot.busy)
      if (!free) return
      this.waiters.shift()!(free)
    }
  }

  async close(): Promise<void> {
    this.closing = true
    await this.starting?.catch(() => undefined)
    await this.browser?.close().catch(() => undefined)
  }
}
