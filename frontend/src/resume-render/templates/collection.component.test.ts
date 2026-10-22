import { describe, expect, it } from 'vitest'
import { MANIFESTS } from './manifests'
import { TEMPLATE_IDS, loadTemplate } from './registry'
import { SECTION_KEYS } from '../model/types'
import { coerceDesign, defaultDesign } from '../theme/tokens'

describe('built-in template collection', () => {
  it('registers 32 independently loadable layouts with complete section coverage', async () => {
    expect(MANIFESTS).toHaveLength(32)
    expect(new Set(MANIFESTS.map(m => m.id)).size).toBe(32)
    expect([...TEMPLATE_IDS].sort()).toEqual(MANIFESTS.map(m => m.id).sort())
    for (const manifest of MANIFESTS) {
      const module = await loadTemplate(manifest.id)
      expect(module.manifest).toBe(manifest)
      expect(module.Layout).toBeTruthy()
      const keys = manifest.regions.flatMap(region => region.sections)
      expect([...keys].sort()).toEqual([...SECTION_KEYS].sort())
    }
  }, 30000)

  it('offers the agreed languages, categories, palettes and usable design settings', () => {
    const added = MANIFESTS.slice(16)
    expect(added.filter(m => m.locale === 'en')).toHaveLength(2)
    expect(added.filter(m => m.category === 'design')).toHaveLength(8)
    expect(added.filter(m => m.category === 'modern')).toHaveLength(4)
    expect(added.filter(m => m.category === 'industry')).toHaveLength(4)
    for (const manifest of added) {
      expect(manifest.maxPages).toBe(2)
      expect(manifest.palettes).toHaveLength(3)
      expect(manifest.headerVariants.map(h => h.id)).toEqual(['standard', 'compact'])
      expect(manifest.photo).toBe(manifest.locale === 'en' ? 'none' : 'optional')
      for (const palette of manifest.palettes) {
        const design = coerceDesign(manifest, { ...defaultDesign(manifest), paletteId: palette.id, headerVariant: 'compact' })
        expect(design.paletteId).toBe(palette.id)
        expect(design.headerVariant).toBe('compact')
        expect(design.fontSize).toBe('M')
      }
    }
  })
})
