import { chromium, expect } from '@playwright/test'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'

// Use the real Vite-compiled component and external sprite, without adding an application route.
const base = 'http://127.0.0.1:5174'
const out = resolve('../.codex_tmp/icon-acceptance-20260906')
await mkdir(out, { recursive: true })
const browser = await chromium.launch({ headless: true })
const results = []
const errors = []
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, reducedMotion: 'reduce' })
  // Identity is outside this read-only glyph check; leave every asset and component response real.
  await page.route('**/api/v1/me', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify({ ok: true, data: null }),
  }))
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })
  page.on('response', response => { if (response.status() >= 400) errors.push(`${response.status()} ${response.url()}`) })
  await page.goto(base, { waitUntil: 'networkidle' })
  const ids = await page.evaluate(async () => {
    const vueUrl = performance.getEntriesByType('resource').map(item => item.name)
      .find(url => new URL(url).pathname.endsWith('/deps/vue.js'))
    if (!vueUrl) throw new Error('The page did not load Vue')
    const { createApp, h, ref } = await import(vueUrl)
    const { default: JobProofIcon } = await import('/src/shared/ui/JobProofIcon.vue')
    const { jobProofIcons } = await import('/src/shared/ui/jobProofIconCatalog.ts')
    const root = document.createElement('div')
    root.id = 'icon-qa'
    document.body.append(root)
    const style = document.createElement('style')
    style.textContent = `
      body { overflow: hidden !important; }
      #icon-qa { position: fixed; inset: 0; z-index: 99999; background: white; padding: 24px; color: #1f2937; }
      #icon-qa .atlas { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 12px; }
      #icon-qa .sample { min-width: 0; height: 120px; border-bottom: 1px solid #e5e7eb; }
      #icon-qa .sample p { margin: 8px 0; font: 12px/1.3 sans-serif; overflow-wrap: anywhere; }
      #icon-qa .glyphs { display: flex; gap: 20px; align-items: center; height: 44px; }
      #icon-qa .blue { color: #2563eb; }
      #icon-qa .unknown { display: none; }
    `
    document.head.append(style)
    const selection = ref([])
    window.setIconQaSelection = ids => { selection.value = ids }
    createApp({ render: () => h('div', [
      h('h1', { style: 'font: 18px sans-serif; margin: 0 0 16px' }, 'JobProof / 16, 20, 24 px + currentColor'),
      h('div', { class: 'atlas' }, selection.value.map(id => h('section', { class: 'sample' }, [
        h('p', id), h('div', { class: 'glyphs' }, [
          ...[16, 20, 24].map(size => h(JobProofIcon, { name: id, size })),
          h(JobProofIcon, { name: id, size: 24, class: 'blue' }),
        ]),
      ]))),
      h('div', { class: 'unknown' }, [h(JobProofIcon, { name: 'not-published' })]),
    ]) }).mount(root)
    return Object.keys(jobProofIcons)
  })
  for (let offset = 0; offset < ids.length; offset += 36) {
    const batch = ids.slice(offset, offset + 36)
    await page.evaluate(ids => window.setIconQaSelection(ids), batch)
    await expect(page.locator('#icon-qa svg')).toHaveCount(batch.length * 4)
    await expect(page.locator('#icon-qa .unknown svg')).toHaveCount(0)
    const geometry = await page.locator('#icon-qa svg').evaluateAll(elements => elements.map(element => {
      const rect = element.getBoundingClientRect()
      return {
        id: element.dataset.jobproofIcon, size: Number(element.getAttribute('width')),
        blue: element.classList.contains('blue'), color: getComputedStyle(element).stroke,
        x: rect.x, y: rect.y, width: rect.width, height: rect.height,
        decorative: element.getAttribute('aria-hidden') === 'true' && element.getAttribute('focusable') === 'false',
        href: element.querySelector('use').getAttribute('href'),
      }
    }))
    const path = resolve(out, `atlas-${offset / 36 + 1}.png`)
    const png = await page.screenshot({ path })
    const pixels = await page.evaluate(async ({ encoded, boxes }) => {
      const image = new Image()
      image.src = `data:image/png;base64,${encoded}`
      await image.decode()
      const canvas = document.createElement('canvas')
      canvas.width = image.width; canvas.height = image.height
      const context = canvas.getContext('2d', { willReadFrequently: true })
      context.drawImage(image, 0, 0)
      return boxes.map(box => {
        const data = context.getImageData(box.x, box.y, box.width, box.height).data
        let ink = 0; let blueInk = 0
        for (let i = 0; i < data.length; i += 4) {
          if (Math.min(data[i], data[i + 1], data[i + 2]) < 220) ink++
          if (data[i + 2] - data[i] > 40) blueInk++
        }
        return { ink, blueInk }
      })
    }, { encoded: png.toString('base64'), boxes: geometry })
    for (const [index, glyph] of geometry.entries()) {
      const signal = pixels[index]
      expect(signal.ink, `${glyph.id} at ${glyph.size}px must render`).toBeGreaterThan(glyph.size)
      if (glyph.blue) expect(signal.blueInk, `${glyph.id} must inherit blue`).toBeGreaterThan(glyph.size)
      expect(glyph.width).toBe(glyph.size)
      expect(glyph.height).toBe(glyph.size)
      expect(glyph.decorative).toBe(true)
      expect(glyph.href.endsWith(`#${glyph.id}`)).toBe(true)
      results.push({ ...glyph, ...signal })
    }
  }
  expect(errors).toEqual([])
} finally {
  await writeFile(resolve(out, 'results.json'), JSON.stringify({ results, errors }, null, 2))
  await browser.close()
}
console.log(JSON.stringify({ icons: results.length / 4, variants: results.length, errors, out }))
