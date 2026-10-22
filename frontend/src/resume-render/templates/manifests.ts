/** All built-in manifests, in gallery order. Cheap to import (no layouts or styles). */
import type { TemplateManifest } from './manifest'
import { manifest as classic } from './classic/manifest'
import { manifest as clarity } from './clarity/manifest'
import { manifest as harvard } from './harvard/manifest'
import { manifest as meridian } from './meridian/manifest'
import { manifest as timeline } from './timeline/manifest'
import { manifest as ledger } from './ledger/manifest'
import { manifest as bold } from './bold/manifest'
import { manifest as aurora } from './aurora/manifest'
import { manifest as editorial } from './editorial/manifest'
import { manifest as inkwash } from './inkwash/manifest'
import { manifest as watercolor } from './watercolor/manifest'
import { manifest as contour } from './contour/manifest'
import { manifest as banker } from './banker/manifest'
import { manifest as engineer } from './engineer/manifest'
import { manifest as academic } from './academic/manifest'
import { manifest as campus } from './campus/manifest'
import { manifest as botanica } from './botanica/manifest'
import { manifest as arcade } from './arcade/manifest'
import { manifest as woven } from './woven/manifest'
import { manifest as mosaic } from './mosaic/manifest'
import { manifest as shoreline } from './shoreline/manifest'
import { manifest as porcelain } from './porcelain/manifest'
import { manifest as constellation } from './constellation/manifest'
import { manifest as atelier } from './atelier/manifest'
import { manifest as prism } from './prism/manifest'
import { manifest as axis } from './axis/manifest'
import { manifest as folio } from './folio/manifest'
import { manifest as orbit } from './orbit/manifest'
import { manifest as product } from './product/manifest'
import { manifest as growth } from './growth/manifest'
import { manifest as clinical } from './clinical/manifest'
import { manifest as consultant } from './consultant/manifest'

export const MANIFESTS: TemplateManifest[] = [
  classic, clarity, harvard, meridian, timeline, ledger, bold, aurora, editorial, inkwash, watercolor, contour, banker, engineer, academic, campus,
  botanica, arcade, woven, mosaic, shoreline, porcelain, constellation, atelier, prism, axis, folio, orbit, product, growth, clinical, consultant,
]

export function manifestOf(id: string): TemplateManifest | undefined {
  return MANIFESTS.find(manifest => manifest.id === id)
}
