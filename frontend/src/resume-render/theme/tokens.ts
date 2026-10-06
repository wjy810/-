/** Design defaults, coercion (incl. v1 → v2) and design → CSS variables. */
import { DATE_FORMATS, type DateFormat } from '../model/dates'
import { SECTION_KEYS, type SectionKey } from '../model/types'
import type { Palette, TemplateManifest } from '../templates/manifest'
import { contrastRatio, ensureContrast, parseHex, tint } from './contrast'
import {
  DESIGN_SCHEMA, FONT_PAIRINGS, FONT_SIZE_PT, FONT_SIZES, LINE_HEIGHT_VALUE, LINE_HEIGHTS, PAGE_MARGIN_MM, PAGE_MARGINS,
  PAGE_TARGETS, PHOTO_MODES, PHOTO_SHAPES, SPACING_SCALE, SPACINGS, type ResumeDesignV2,
} from './design'
import { FONT_STACKS } from './fonts'

export const PAPER_MM = { A4: { width: 210, height: 297 }, LETTER: { width: 215.9, height: 279.4 } } as const
export const PX_PER_MM = 96 / 25.4

function oneOf<T extends string>(values: readonly T[], value: unknown, fallback: T): T {
  return typeof value === 'string' && (values as readonly string[]).includes(value) ? value as T : fallback
}
function sectionKeys(value: unknown): SectionKey[] {
  return Array.isArray(value) ? [...new Set(value.filter((key): key is SectionKey => (SECTION_KEYS as readonly string[]).includes(key)))] : []
}

export function defaultDesign(manifest: TemplateManifest): ResumeDesignV2 {
  const base: ResumeDesignV2 = {
    schemaVersion: DESIGN_SCHEMA,
    paletteId: manifest.palettes[0]!.id,
    customAccent: null,
    fontPairing: manifest.fontPairings[0]!,
    fontSize: 'M',
    lineHeight: 'NORMAL',
    spacing: 'NORMAL',
    pageMargin: 'STANDARD',
    headerVariant: manifest.headerVariants[0]?.id ?? 'default',
    photo: { mode: 'AUTO', shape: 'CIRCLE' },
    contactIcons: true,
    dateFormat: manifest.locale === 'en' ? 'MMM YYYY' : 'YYYY.MM',
    pageTarget: 'AUTO',
    paperSize: manifest.paperSizes[0]!,
    decorations: true,
    sectionOrder: manifest.regions.flatMap(region => region.sections),
    hiddenSections: [],
    sectionTitles: {},
    regionAssignments: {},
  }
  return { ...base, ...manifest.defaults, photo: { ...base.photo, ...manifest.defaults.photo } }
}

const V1_FONT: Record<string, ResumeDesignV2['fontPairing']> = { MODERN_SANS: 'sans', CLASSIC_SERIF: 'serif' }
const V1_SIZE: Record<string, ResumeDesignV2['fontSize']> = { SMALL: 'S', STANDARD: 'M', LARGE: 'L' }
const V1_LINE: Record<string, ResumeDesignV2['lineHeight']> = { COMPACT: 'COMPACT', STANDARD: 'NORMAL', AIRY: 'RELAXED' }
const V1_DENSITY: Record<string, ResumeDesignV2['spacing']> = { COMPACT: 'TIGHT', STANDARD: 'NORMAL', AIRY: 'RELAXED' }
const V1_DATE: Record<string, DateFormat> = { YYYY_DOT_MM: 'YYYY.MM', YYYY_CN_MM: 'YYYY年MM月' }

/** Legacy resume-design-v1 settings → v2 fields (unknown values fall back to the template default). */
export function fromV1(raw: Record<string, unknown>, manifest: TemplateManifest): Partial<ResumeDesignV2> {
  const accent = typeof raw.accentColor === 'string' ? raw.accentColor.toLowerCase() : ''
  const palette = manifest.palettes.find(item => item.accent.toLowerCase() === accent)
  return {
    fontPairing: V1_FONT[String(raw.fontPreset)],
    fontSize: V1_SIZE[String(raw.fontScale)],
    lineHeight: V1_LINE[String(raw.lineHeight)],
    spacing: V1_DENSITY[String(raw.density)],
    pageMargin: oneOf(PAGE_MARGINS, raw.pageMargin, 'STANDARD'),
    dateFormat: V1_DATE[String(raw.dateFormat)],
    photo: { mode: oneOf(PHOTO_MODES, raw.photoMode, 'AUTO'), shape: 'CIRCLE' },
    paletteId: palette?.id,
    customAccent: !palette && parseHex(accent) ? accent : null,
    sectionOrder: sectionKeys(raw.sectionOrder),
    hiddenSections: sectionKeys(raw.hiddenSections),
  }
}

/** Any stored settings → a valid v2 design for this template. */
export function coerceDesign(manifest: TemplateManifest, input: unknown): ResumeDesignV2 {
  const defaults = defaultDesign(manifest)
  let raw = input && typeof input === 'object' ? input as Record<string, unknown> : {}
  if (raw.schemaVersion === 'resume-design-v1') raw = Object.fromEntries(Object.entries(fromV1(raw, manifest)).filter(([, v]) => v !== undefined))
  const photo = raw.photo && typeof raw.photo === 'object' ? raw.photo as Record<string, unknown> : {}
  const order = sectionKeys(raw.sectionOrder)
  const titles: ResumeDesignV2['sectionTitles'] = {}
  if (raw.sectionTitles && typeof raw.sectionTitles === 'object') {
    for (const [key, value] of Object.entries(raw.sectionTitles as Record<string, unknown>)) {
      // eslint-disable-next-line no-control-regex
      const clean = typeof value === 'string' ? value.replace(/[\u0000-\u001f]/g, '').trim().slice(0, 16) : ''
      if (clean && (SECTION_KEYS as readonly string[]).includes(key)) titles[key as SectionKey] = clean
    }
  }
  const regionIds = new Set(manifest.regions.map(region => region.id))
  const assignments: ResumeDesignV2['regionAssignments'] = {}
  if (raw.regionAssignments && typeof raw.regionAssignments === 'object') {
    for (const [key, value] of Object.entries(raw.regionAssignments as Record<string, unknown>)) {
      if ((SECTION_KEYS as readonly string[]).includes(key) && typeof value === 'string' && regionIds.has(value)) assignments[key as SectionKey] = value
    }
  }
  return {
    schemaVersion: DESIGN_SCHEMA,
    paletteId: manifest.palettes.some(p => p.id === raw.paletteId) ? String(raw.paletteId) : defaults.paletteId,
    customAccent: typeof raw.customAccent === 'string' && parseHex(raw.customAccent) ? raw.customAccent.toLowerCase() : null,
    fontPairing: manifest.fontPairings.includes(raw.fontPairing as never) ? oneOf(FONT_PAIRINGS, raw.fontPairing, defaults.fontPairing) : defaults.fontPairing,
    fontSize: oneOf(FONT_SIZES, raw.fontSize, defaults.fontSize),
    lineHeight: oneOf(LINE_HEIGHTS, raw.lineHeight, defaults.lineHeight),
    spacing: oneOf(SPACINGS, raw.spacing, defaults.spacing),
    pageMargin: oneOf(PAGE_MARGINS, raw.pageMargin, defaults.pageMargin),
    headerVariant: manifest.headerVariants.some(v => v.id === raw.headerVariant) ? String(raw.headerVariant) : defaults.headerVariant,
    photo: {
      mode: manifest.photo === 'none' ? 'HIDE' : oneOf(PHOTO_MODES, photo.mode, defaults.photo.mode),
      shape: oneOf(PHOTO_SHAPES, photo.shape, defaults.photo.shape),
    },
    contactIcons: typeof raw.contactIcons === 'boolean' ? raw.contactIcons : defaults.contactIcons,
    dateFormat: oneOf(DATE_FORMATS, raw.dateFormat, defaults.dateFormat),
    pageTarget: oneOf(PAGE_TARGETS, raw.pageTarget, defaults.pageTarget),
    paperSize: manifest.paperSizes.includes(raw.paperSize as never) ? raw.paperSize as ResumeDesignV2['paperSize'] : defaults.paperSize,
    decorations: typeof raw.decorations === 'boolean' ? raw.decorations : defaults.decorations,
    sectionOrder: order.length ? [...order, ...defaults.sectionOrder.filter(key => !order.includes(key))] : defaults.sectionOrder,
    hiddenSections: sectionKeys(raw.hiddenSections),
    sectionTitles: titles,
    regionAssignments: assignments,
  }
}

export function paletteOf(manifest: TemplateManifest, design: ResumeDesignV2): Palette {
  return manifest.palettes.find(item => item.id === design.paletteId) ?? manifest.palettes[0]!
}

export interface ResolvedTheme {
  accent: string
  accentAdjusted: boolean
  palette: Palette
  vars: Record<string, string>
  pageMm: { width: number; height: number }
}

export function resolveTheme(manifest: TemplateManifest, design: ResumeDesignV2): ResolvedTheme {
  const palette = paletteOf(manifest, design)
  const paper = palette.paper ?? '#ffffff'
  const ink = palette.ink ?? '#1d2129'
  const requested = design.customAccent ?? palette.accent
  const { color: accent, adjusted } = ensureContrast(requested, paper, 4.5)
  const muted = palette.muted ?? (contrastRatio(tint(ink, paper, 0.62), paper) >= 4.5 ? tint(ink, paper, 0.62) : tint(ink, paper, 0.72))
  const fonts = FONT_STACKS[design.fontPairing]
  const size = FONT_SIZE_PT[design.fontSize]
  const margin = PAGE_MARGIN_MM[design.pageMargin]
  const pageMm = PAPER_MM[design.paperSize]
  return {
    accent,
    accentAdjusted: Boolean(design.customAccent) && adjusted,
    palette,
    pageMm,
    vars: {
      '--r-accent': accent,
      '--r-accent-soft': tint(accent, paper, 0.07),
      '--r-accent-tint': tint(accent, paper, 0.16),
      '--r-ink': ink,
      '--r-muted': muted,
      '--r-rule': tint(ink, paper, 0.18),
      '--r-paper': paper,
      // Fixed paper colours: text on accent fills, and the vermilion seal of the ink-wash template.
      '--r-on-accent': '#ffffff',
      '--r-seal': '#a23b2c',
      '--r-seal-ink': '#fbf4ec',
      '--r-font-body': fonts.body,
      '--r-font-heading': fonts.heading,
      '--r-font-mono': fonts.mono,
      '--r-size': `${size}pt`,
      '--r-size-sm': `${(size - 1).toFixed(2)}pt`,
      '--r-size-title': `${(size + 0.75).toFixed(2)}pt`,
      '--r-size-section': `${(size + 1.75).toFixed(2)}pt`,
      '--r-leading': String(LINE_HEIGHT_VALUE[design.lineHeight]),
      '--r-gap': String(SPACING_SCALE[design.spacing]),
      '--r-margin-x': `${margin}mm`,
      '--r-margin-y': `${(margin * 0.9).toFixed(1)}mm`,
      '--r-page-w': `${pageMm.width}mm`,
      '--r-page-h': `${pageMm.height}mm`,
    },
  }
}
