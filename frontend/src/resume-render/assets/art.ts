/** Decorative raster art (docs/phase2/04 §6.1), exported by design/template-assets/export.py. */
const FILES: Record<string, string> = {
  'paper-warm': new URL('./art/paper-warm.jpg', import.meta.url).href,
  'paper-cool': new URL('./art/paper-cool.jpg', import.meta.url).href,
  'paper-linen': new URL('./art/paper-linen.jpg', import.meta.url).href,
  'ink-mountains': new URL('./art/ink-mountains.webp', import.meta.url).href,
  'ink-bamboo': new URL('./art/ink-bamboo.webp', import.meta.url).href,
  'ink-plum': new URL('./art/ink-plum.webp', import.meta.url).href,
  'ink-wash-band': new URL('./art/ink-wash-band.webp', import.meta.url).href,
  'wash-indigo': new URL('./art/wash-indigo.webp', import.meta.url).href,
  'wash-sage': new URL('./art/wash-sage.webp', import.meta.url).href,
  'wash-terracotta': new URL('./art/wash-terracotta.webp', import.meta.url).href,
  'topo-lines': new URL('./art/topo-lines.webp', import.meta.url).href,
  'botanical-corner': new URL('./art/botanical-corner.webp', import.meta.url).href,
  'aurora-soft': new URL('./art/aurora-soft.webp', import.meta.url).href,
}

export function artUrl(key: string): string {
  return FILES[key] ?? ''
}
