/** Renderer configuration from the environment (docs/phase2/02 §6). */
import { resolve } from 'node:path'

function int(name: string, fallback: number): number {
  const value = Number(process.env[name])
  return Number.isFinite(value) && value > 0 ? Math.floor(value) : fallback
}

export interface RendererConfig {
  host: string
  port: number
  token: string
  printDir: string
  chromiumPath: string | undefined
  poolSize: number
  renderTimeoutMs: number
  queueTimeoutMs: number
  maxBodyBytes: number
  recyclePageAfter: number
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): RendererConfig {
  const token = env.RENDERER_TOKEN ?? ''
  if (token.length < 16 && env.RENDERER_ALLOW_WEAK_TOKEN !== 'true') {
    throw new Error('RENDERER_TOKEN must be at least 16 characters (set RENDERER_ALLOW_WEAK_TOKEN=true only for local tests)')
  }
  return {
    host: env.RENDERER_HOST ?? '0.0.0.0',
    port: int('RENDERER_PORT', 3100),
    token,
    printDir: resolve(env.RENDERER_PRINT_DIR ?? '../frontend/dist'),
    chromiumPath: env.RENDERER_CHROMIUM_PATH || undefined,
    poolSize: int('RENDERER_POOL_SIZE', 2),
    renderTimeoutMs: int('RENDERER_TIMEOUT_MS', 20_000),
    queueTimeoutMs: int('RENDERER_QUEUE_TIMEOUT_MS', 20_000),
    maxBodyBytes: int('RENDERER_MAX_BODY_BYTES', 2 * 1024 * 1024),
    recyclePageAfter: int('RENDERER_RECYCLE_AFTER', 200),
  }
}
