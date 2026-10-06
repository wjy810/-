import type { ReleaseSummary } from './types'

export type UpdateMetadata = {
  title: string
  description: string
  canonicalPath: string
  jsonLd: Record<string, unknown>
}

export function buildUpdateMetadata(release?: ReleaseSummary | null): UpdateMetadata {
  const title = release
    ? `${release.versionLabel} ${release.title} · JobProof AI`
    : '系统更新日志 · JobProof AI'
  const description = release?.summary || '查看 JobProof AI 已正式发布的真实产品更新、体验优化和问题修复。'
  const canonicalPath = release ? `/updates/${release.slug}` : '/updates'
  return {
    title,
    description,
    canonicalPath,
    jsonLd: release ? {
      '@context': 'https://schema.org',
      '@type': 'TechArticle',
      headline: `${release.versionLabel} ${release.title}`,
      description,
      datePublished: release.publishedAt || release.updatedAt,
      dateModified: release.updatedAt,
      author: { '@type': 'Organization', name: 'JobProof AI' },
      mainEntityOfPage: canonicalPath,
    } : {
      '@context': 'https://schema.org',
      '@type': 'CollectionPage',
      name: 'JobProof AI 系统更新日志',
      description,
      url: canonicalPath,
    },
  }
}

export function applyUpdateMetadata(metadata: UpdateMetadata): () => void {
  if (typeof document === 'undefined') return () => undefined
  const previousTitle = document.title
  const touched: Array<{ element: HTMLElement; previous: string | null; attribute: string; created: boolean }> = []
  document.title = metadata.title

  const setMeta = (selector: string, attributes: Record<string, string>, content: string): void => {
    let element = document.head.querySelector<HTMLMetaElement>(selector)
    const created = !element
    if (!element) { element = document.createElement('meta'); Object.entries(attributes).forEach(([key, value]) => element!.setAttribute(key, value)); document.head.appendChild(element) }
    touched.push({ element, previous: element.getAttribute('content'), attribute: 'content', created })
    element.setAttribute('content', content)
  }
  setMeta('meta[name="description"]', { name: 'description' }, metadata.description)
  setMeta('meta[property="og:title"]', { property: 'og:title' }, metadata.title)
  setMeta('meta[property="og:description"]', { property: 'og:description' }, metadata.description)
  setMeta('meta[property="og:type"]', { property: 'og:type' }, 'article')

  let canonical = document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]')
  const canonicalCreated = !canonical
  if (!canonical) { canonical = document.createElement('link'); canonical.rel = 'canonical'; document.head.appendChild(canonical) }
  touched.push({ element: canonical, previous: canonical.getAttribute('href'), attribute: 'href', created: canonicalCreated })
  canonical.href = new URL(metadata.canonicalPath, window.location.origin).toString()

  const script = document.createElement('script')
  script.type = 'application/ld+json'
  script.dataset.jobproofUpdates = 'true'
  script.textContent = JSON.stringify(metadata.jsonLd)
  document.head.appendChild(script)

  return () => {
    document.title = previousTitle
    script.remove()
    for (const item of touched) {
      if (item.created) item.element.remove()
      else if (item.previous == null) item.element.removeAttribute(item.attribute)
      else item.element.setAttribute(item.attribute, item.previous)
    }
  }
}
