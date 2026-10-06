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

export const MANIFESTS: TemplateManifest[] = [classic, clarity, harvard, meridian, timeline, ledger, bold, aurora, editorial, inkwash, watercolor, contour, banker, engineer, academic, campus]

export function manifestOf(id: string): TemplateManifest | undefined {
  return MANIFESTS.find(manifest => manifest.id === id)
}
