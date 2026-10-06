/**
 * Loopback-only static server for the built print entry. Chromium loads print.html from here: module
 * scripts do not run from file://, and a single known origin makes network isolation simple.
 */
import { createReadStream } from 'node:fs'
import { stat } from 'node:fs/promises'
import { createServer, type Server } from 'node:http'
import type { AddressInfo } from 'node:net'
import { extname, join, normalize, sep } from 'node:path'

const TYPES: Record<string, string> = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.woff2': 'font/woff2', '.woff': 'font/woff', '.webp': 'image/webp', '.png': 'image/png', '.svg': 'image/svg+xml',
  '.jpg': 'image/jpeg', '.json': 'application/json',
}

export async function startStaticServer(root: string): Promise<{ origin: string; close: () => Promise<void> }> {
  const base = normalize(root + sep)
  const server: Server = createServer(async (request, response) => {
    try {
      const path = decodeURIComponent(new URL(request.url ?? '/', 'http://local').pathname)
      const file = normalize(join(base, path === '/' ? 'print.html' : path))
      if (request.method !== 'GET' || !file.startsWith(base)) {
        response.writeHead(404).end()
        return
      }
      const info = await stat(file)
      if (!info.isFile()) throw new Error('not a file')
      response.writeHead(200, { 'Content-Type': TYPES[extname(file)] ?? 'application/octet-stream', 'Content-Length': info.size, 'Cache-Control': 'max-age=31536000' })
      createReadStream(file).pipe(response)
    } catch {
      response.writeHead(404).end()
    }
  })
  await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve))
  const { port } = server.address() as AddressInfo
  return { origin: `http://127.0.0.1:${port}`, close: () => new Promise(resolve => server.close(() => resolve())) }
}
