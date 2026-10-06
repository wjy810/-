export function formatResumeContactLine(basics: Record<string, unknown>): string {
  const links = Array.isArray(basics.links)
    ? basics.links.map((link) => {
      if (typeof link === 'string') return link
      if (!link || typeof link !== 'object' || Array.isArray(link)) return ''
      const value = link as Record<string, unknown>
      return value.url ?? value.value ?? value.href ?? ''
    })
    : []

  return [basics.phone, basics.email, basics.location, ...links]
    .map((item) => String(item ?? '').trim())
    .filter(Boolean)
    .join(' · ')
}
