/** Registry of the 3D clay illustrations (docs/04 §4.1). Files: shared/assets/illustrations/*.webp */
const files = import.meta.glob<string>('../assets/illustrations/*.webp', { eager: true, import: 'default', query: '?url' })

export type IllustrationName =
  | 'account-security' | 'admin-channel' | 'ai-orb' | 'ai-usage' | 'application-board' | 'auth-key' | 'calendar' | 'campus-partner'
  | 'career-profile'
  | 'dash-interview' | 'dash-library' | 'dash-match' | 'dash-planning' | 'data-export' | 'decor-scatter' | 'delete-bin'
  | 'empty-canvas' | 'empty-folder' | 'empty-history' | 'empty-interview' | 'empty-match' | 'empty-notification'
  | 'empty-progress' | 'empty-resume' | 'empty-search' | 'empty-templates' | 'empty-updates' | 'error-plane'
  | 'hero-cap' | 'hero-chat' | 'hero-check' | 'hero-magnifier' | 'hero-pencil' | 'hero-plane'
  | 'hero-resume' | 'hero-sparkles' | 'hero-target'
  | 'identity-graduate' | 'identity-professional' | 'identity-student'
  | 'ink-pen' | 'job-offer' | 'membership-pro' | 'privacy-shield' | 'redeem-success' | 'redeem-ticket'
  | 'resume-import' | 'resume-receipt' | 'resume-shared' | 'rocket' | 'success-confetti' | 'success-trophy'
  | 'upload-cloud' | 'voice-headset' | 'welcome-desk'

const byName = new Map<string, string>()
for (const [path, url] of Object.entries(files)) {
  const name = path.split('/').pop()?.replace(/\.webp$/, '')
  if (name) byName.set(name, url)
}

export function illustrationUrl(name: IllustrationName): string | undefined {
  return byName.get(name)
}
