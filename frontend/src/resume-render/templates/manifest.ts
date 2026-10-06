import type { Component } from 'vue'
import type { FontPairing, ResumeDesignV2 } from '../theme/design'
import type { SectionKey } from '../model/types'

export type TemplateCategory = 'steady' | 'modern' | 'design' | 'industry'

export interface Palette {
  id: string
  name: string
  accent: string
  /** Body ink; defaults to a near-black. */
  ink?: string
  muted?: string
  paper?: string
  /** Texture / ornament asset key for decorative templates (see assets.ts). */
  art?: string
}

export interface RegionSpec {
  id: string
  /** Sections placed here unless the user moves them (regionAssignments). */
  sections: SectionKey[]
}

export interface TemplateManifest {
  id: string
  revision: number
  name: string
  nameEn: string
  category: TemplateCategory
  summary: string
  bestFor: string
  tags: string[]
  maxPages: number
  locale: 'zh-CN' | 'en'
  paperSizes: Array<'A4' | 'LETTER'>
  fontPairings: FontPairing[]
  palettes: Palette[]
  headerVariants: Array<{ id: string; name: string }>
  photo: 'none' | 'optional'
  regions: RegionSpec[]
  atsLevel: 'strict' | 'standard'
  decorations: boolean
  sectionTitles?: Partial<Record<SectionKey, string>>
  defaults: Partial<ResumeDesignV2>
}

export interface TemplateModule {
  manifest: TemplateManifest
  /** Page shell: header, region containers (<slot name="region" :id>), decorations. */
  Layout: Component
}

export function defineTemplate(module: TemplateModule): TemplateModule {
  return module
}
