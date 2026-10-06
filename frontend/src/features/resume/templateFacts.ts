/** Short, user-facing facts about a built-in template for cards and the detail page. */
import type { TemplateCategory, TemplateManifest } from '@/resume-render/templates/manifest'

export const TEMPLATE_CATEGORY_LABELS: Record<TemplateCategory, string> = {
  steady: '稳健', modern: '现代', design: '设计感', industry: '行业',
}

export function templateFacts(manifest: TemplateManifest): string[] {
  const facts = [TEMPLATE_CATEGORY_LABELS[manifest.category], manifest.maxPages === 1 ? '严格单页' : `最多 ${manifest.maxPages} 页`]
  facts.push(manifest.photo === 'optional' ? '可放照片' : '无照片')
  facts.push(manifest.locale === 'en' ? '英文' : '中文')
  if (manifest.paperSizes.includes('LETTER')) facts.push('A4 / Letter')
  if (manifest.atsLevel === 'strict') facts.push('ATS 友好')
  if (manifest.decorations) facts.push('含底纹装饰')
  return facts
}
