/** Template id → lazily loaded module (layout + styles). Manifests are listed eagerly in manifests.ts. */
import type { TemplateModule } from './manifest'

const LOADERS: Record<string, () => Promise<{ default: TemplateModule }>> = {
  classic: () => import('./classic'),
  meridian: () => import('./meridian'),
  timeline: () => import('./timeline'),
}

const cache = new Map<string, Promise<TemplateModule>>()

export function hasTemplate(id: string): boolean {
  return id in LOADERS
}

export function loadTemplate(id: string): Promise<TemplateModule> {
  const loader = LOADERS[id]
  if (!loader) return Promise.reject(new Error(`Unknown resume template: ${id}`))
  let pending = cache.get(id)
  if (!pending) {
    pending = loader().then(module => module.default)
    cache.set(id, pending)
  }
  return pending
}

export const TEMPLATE_IDS = Object.keys(LOADERS)
