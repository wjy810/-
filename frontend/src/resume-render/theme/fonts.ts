import type { FontPairing } from './design'

const SANS = "'Inter', 'Noto Sans SC', sans-serif"
const SERIF = "'Source Serif 4', 'Noto Serif SC', serif"
const MONO = "'JetBrains Mono', 'Noto Sans SC', monospace"

export const FONT_STACKS: Record<FontPairing, { body: string; heading: string; mono: string }> = {
  sans: { body: SANS, heading: SANS, mono: SANS },
  serif: { body: SERIF, heading: SERIF, mono: SERIF },
  mixed: { body: SANS, heading: SERIF, mono: SANS },
  tech: { body: SANS, heading: SANS, mono: MONO },
}

export const FONT_PAIRING_LABELS: Record<FontPairing, string> = {
  sans: '现代无衬线', serif: '经典衬线', mixed: '编辑（衬线标题）', tech: '技术（等宽点缀）',
}
