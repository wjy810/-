/** Design settings v2 (docs/phase2/03 §6.2). The backend validates the same shape against the manifest. */
import type { DateFormat } from '../model/dates'
import type { SectionKey } from '../model/types'

export const DESIGN_SCHEMA = 'resume-design-v2'

export const FONT_SIZES = ['XS', 'S', 'M', 'L', 'XL'] as const
export const LINE_HEIGHTS = ['COMPACT', 'NORMAL', 'RELAXED'] as const
export const SPACINGS = ['TIGHT', 'NORMAL', 'RELAXED'] as const
export const PAGE_MARGINS = ['NARROW', 'STANDARD', 'WIDE'] as const
export const PAGE_TARGETS = ['AUTO', 'ONE', 'TWO'] as const
export const PHOTO_MODES = ['AUTO', 'SHOW', 'HIDE'] as const
export const PHOTO_SHAPES = ['CIRCLE', 'ROUNDED', 'SQUARE'] as const
export const FONT_PAIRINGS = ['sans', 'serif', 'mixed', 'tech'] as const

export type FontSize = (typeof FONT_SIZES)[number]
export type LineHeight = (typeof LINE_HEIGHTS)[number]
export type Spacing = (typeof SPACINGS)[number]
export type PageMargin = (typeof PAGE_MARGINS)[number]
export type PageTarget = (typeof PAGE_TARGETS)[number]
export type FontPairing = (typeof FONT_PAIRINGS)[number]

export interface ResumeDesignV2 {
  schemaVersion: typeof DESIGN_SCHEMA
  paletteId: string
  customAccent: string | null
  fontPairing: FontPairing
  fontSize: FontSize
  lineHeight: LineHeight
  spacing: Spacing
  pageMargin: PageMargin
  headerVariant: string
  photo: { mode: (typeof PHOTO_MODES)[number]; shape: (typeof PHOTO_SHAPES)[number] }
  contactIcons: boolean
  dateFormat: DateFormat
  pageTarget: PageTarget
  paperSize: 'A4' | 'LETTER'
  decorations: boolean
  sectionOrder: SectionKey[]
  hiddenSections: SectionKey[]
  sectionTitles: Partial<Record<SectionKey, string>>
  regionAssignments: Partial<Record<SectionKey, string>>
}

/** Base point sizes per step (docs/phase2/04 §2.2). */
export const FONT_SIZE_PT: Record<FontSize, number> = { XS: 8.75, S: 9.25, M: 9.75, L: 10.25, XL: 10.75 }
export const LINE_HEIGHT_VALUE: Record<LineHeight, number> = { COMPACT: 1.42, NORMAL: 1.55, RELAXED: 1.68 }
export const SPACING_SCALE: Record<Spacing, number> = { TIGHT: 0.72, NORMAL: 1, RELAXED: 1.28 }
export const PAGE_MARGIN_MM: Record<PageMargin, number> = { NARROW: 12, STANDARD: 16, WIDE: 20 }

/** "One-click compact" steps, applied in order until the content fits (docs/phase2/03 §3.3). */
export function compactSteps(design: ResumeDesignV2): ResumeDesignV2[] {
  const steps: ResumeDesignV2[] = []
  let current = design
  const push = (patch: Partial<ResumeDesignV2>) => { current = { ...current, ...patch }; steps.push(current) }
  if (current.spacing !== 'TIGHT') push({ spacing: current.spacing === 'RELAXED' ? 'NORMAL' : 'TIGHT' })
  if (current.lineHeight !== 'COMPACT') push({ lineHeight: current.lineHeight === 'RELAXED' ? 'NORMAL' : 'COMPACT' })
  const sizeIndex = FONT_SIZES.indexOf(current.fontSize)
  if (sizeIndex > 1) push({ fontSize: FONT_SIZES[sizeIndex - 1]! })
  if (current.pageMargin !== 'NARROW') push({ pageMargin: current.pageMargin === 'WIDE' ? 'STANDARD' : 'NARROW' })
  return steps
}
