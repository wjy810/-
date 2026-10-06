/** Template id → lazily loaded module (layout + styles). Manifests are listed eagerly in manifests.ts. */
import type { TemplateModule } from './manifest'

const LOADERS: Record<string, () => Promise<{ default: TemplateModule }>> = {
  classic: () => import('./classic'),
  clarity: () => import('./clarity'),
  harvard: () => import('./harvard'),
  meridian: () => import('./meridian'),
  timeline: () => import('./timeline'),
  ledger: () => import('./ledger'),
  bold: () => import('./bold'),
  aurora: () => import('./aurora'),
  editorial: () => import('./editorial'),
  inkwash: () => import('./inkwash'),
  watercolor: () => import('./watercolor'),
  contour: () => import('./contour'),
  banker: () => import('./banker'),
  engineer: () => import('./engineer'),
  academic: () => import('./academic'),
  campus: () => import('./campus'),
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
