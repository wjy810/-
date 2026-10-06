/** All built-in manifests, in gallery order. Cheap to import (no layouts or styles). */
import type { TemplateManifest } from './manifest'
import { manifest as classic } from './classic/manifest'
import { manifest as meridian } from './meridian/manifest'
import { manifest as timeline } from './timeline/manifest'

export const MANIFESTS: TemplateManifest[] = [classic, meridian, timeline]

export function manifestOf(id: string): TemplateManifest | undefined {
  return MANIFESTS.find(manifest => manifest.id === id)
}
