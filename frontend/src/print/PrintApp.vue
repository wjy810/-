<script setup lang="ts">
/**
 * window.__JP_RENDER__(payload) renders one resume and resolves with { pageCount, overflow… } once
 * fonts and images are ready, so the renderer can call page.pdf(). In development, ?template=…&sample=…
 * renders a sample directly (used for screenshots and visual review).
 */
import { nextTick, onMounted, shallowRef } from 'vue'
import ResumeDocument, { type LayoutResult } from '@/resume-render/components/ResumeDocument.vue'
import type { TemplateModule } from '@/resume-render/templates/manifest'
import { loadTemplate } from '@/resume-render/templates/registry'
import { SAMPLES, type SampleId } from '@/resume-render/samples'

interface PrintPayload {
  templateId: string
  design?: unknown
  content: unknown
  photo?: string | null
  title?: string
}

declare global {
  interface Window {
    __JP_RENDER__?: (payload: PrintPayload) => Promise<LayoutResult & { paperSize: string }>
    __JP_READY__?: boolean
    /** Last layout result, for visual review scripts. */
    __JP_LAYOUT__?: LayoutResult
  }
}

const template = shallowRef<TemplateModule | null>(null)
const current = shallowRef<PrintPayload | null>(null)
const renderKey = shallowRef(0)
let settle: ((result: LayoutResult) => void) | null = null

function setPaper(size: string): void {
  const style = document.getElementById('page-size')
  if (style) style.textContent = `@page { size: ${size === 'LETTER' ? 'letter' : 'A4'}; margin: 0 }`
}

async function imagesReady(): Promise<void> {
  await Promise.all(Array.from(document.images).map(image => (image.complete ? Promise.resolve() : image.decode().catch(() => undefined))))
}

async function render(payload: PrintPayload) {
  const module = await loadTemplate(payload.templateId)
  const layout = new Promise<LayoutResult>((resolve) => { settle = resolve })
  template.value = module
  current.value = payload
  renderKey.value += 1
  const result = await layout
  await nextTick()
  await document.fonts.ready
  await imagesReady()
  const paperSize = (payload.design as { paperSize?: string } | undefined)?.paperSize === 'LETTER' && module.manifest.paperSizes.includes('LETTER') ? 'LETTER' : 'A4'
  setPaper(paperSize)
  document.title = payload.title || 'Resume'
  window.__JP_LAYOUT__ = result
  return { ...result, paperSize }
}

function onLayout(result: LayoutResult): void {
  settle?.(result)
  settle = null
}

window.__JP_RENDER__ = render

onMounted(async () => {
  const params = new URLSearchParams(location.search)
  const templateId = params.get('template')
  if (templateId) {
    const sample = (params.get('sample') ?? 'professional') as SampleId
    const design: Record<string, unknown> = {}
    for (const key of ['paletteId', 'fontPairing', 'fontSize', 'lineHeight', 'spacing', 'pageMargin', 'headerVariant', 'pageTarget', 'paperSize']) {
      const value = params.get(key)
      if (value) design[key] = value
    }
    await render({ templateId, design, content: SAMPLES[sample] ?? SAMPLES.professional, photo: params.get('photo') })
  }
  window.__JP_READY__ = true
  document.documentElement.dataset.ready = '1'
})
</script>

<template>
  <ResumeDocument
    v-if="template && current"
    :key="renderKey"
    :template="template"
    :content="current.content"
    :design="current.design"
    :photo="current.photo ?? null"
    mode="print"
    :fit="false"
    @layout="onLayout"
  />
</template>
