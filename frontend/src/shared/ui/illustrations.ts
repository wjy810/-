/** Registry of the 3D clay illustrations (docs/04 §4.1). Files: shared/assets/illustrations/*.webp */
const files = import.meta.glob<string>('../assets/illustrations/*.webp', { eager: true, import: 'default', query: '?url' })

export type IllustrationName =
  | 'ai-orb' | 'auth-key' | 'dash-interview' | 'dash-library' | 'dash-match' | 'dash-planning'
  | 'empty-canvas' | 'empty-folder' | 'empty-interview' | 'empty-match' | 'empty-notification'
  | 'empty-resume' | 'empty-search' | 'empty-templates' | 'error-plane'
  | 'hero-cap' | 'hero-chat' | 'hero-check' | 'hero-magnifier' | 'hero-pencil' | 'hero-plane'
  | 'hero-resume' | 'hero-sparkles' | 'hero-target'
  | 'identity-graduate' | 'identity-professional' | 'identity-student'
  | 'ink-pen' | 'rocket' | 'success-confetti' | 'success-trophy' | 'welcome-desk'

const byName = new Map<string, string>()
for (const [path, url] of Object.entries(files)) {
  const name = path.split('/').pop()?.replace(/\.webp$/, '')
  if (name) byName.set(name, url)
}

export function illustrationUrl(name: IllustrationName): string | undefined {
  return byName.get(name)
}
