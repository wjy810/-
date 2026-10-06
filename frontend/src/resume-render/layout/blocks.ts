/** RenderDocument → ordered content blocks per region (docs/phase2/03 §3.2). */
import type { ListItem, RenderDocument, RenderSection, SectionKey, SkillGroup, TimelineItem } from '../model/types'
import type { ResumeDesignV2 } from '../theme/design'
import type { TemplateManifest } from '../templates/manifest'

export type BlockPart =
  | { type: 'text' }
  | { type: 'timeline'; item: TimelineItem; head: boolean; from: number; to: number; paragraphs: boolean }
  | { type: 'skill'; group: SkillGroup }
  | { type: 'list'; item: ListItem }

export interface Block {
  id: string
  region: string
  section: RenderSection
  sectionKey: SectionKey
  /** The section heading travels with the first block so it is never stranded at the bottom of a page. */
  withHeading: boolean
  /** 0-based position of the section in reading order (for numbered headings). */
  sectionIndex: number
  /** The next block continues the same entry (a split timeline entry): bullet spacing follows. */
  joined: boolean
  /** A one-line row (skill group, list item) followed by another row of its section. */
  row: boolean
  part: BlockPart
}

/** Bullets kept with an entry's heading before the rest may continue on the next page. */
const HEAD_BULLETS = 2
const SPLIT_FROM = 5

export function regionFor(key: SectionKey, manifest: TemplateManifest, design: ResumeDesignV2): string {
  const assigned = design.regionAssignments[key]
  if (assigned && manifest.regions.some(region => region.id === assigned)) return assigned
  return manifest.regions.find(region => region.sections.includes(key))?.id ?? manifest.regions[0]!.id
}

function timelineParts(item: TimelineItem): BlockPart[] {
  const count = item.body.bullets.length
  if (count < SPLIT_FROM) return [{ type: 'timeline', item, head: true, from: 0, to: count, paragraphs: true }]
  const parts: BlockPart[] = [{ type: 'timeline', item, head: true, from: 0, to: HEAD_BULLETS, paragraphs: true }]
  for (let from = HEAD_BULLETS; from < count; from += 1) {
    parts.push({ type: 'timeline', item, head: false, from, to: from + 1, paragraphs: false })
  }
  return parts
}

function partsOf(section: RenderSection): BlockPart[] {
  switch (section.kind) {
    case 'text': return [{ type: 'text' }]
    case 'timeline': return section.items.flatMap(timelineParts)
    case 'skills': return section.groups.map(group => ({ type: 'skill', group }))
    case 'list': return section.items.map(item => ({ type: 'list', item }))
  }
}

function partId(part: BlockPart): string {
  switch (part.type) {
    case 'text': return 'text'
    case 'timeline': return `${part.item.id}:${part.from}`
    case 'skill': return part.group.id
    case 'list': return part.item.id
  }
}

export function buildBlocks(document: RenderDocument, manifest: TemplateManifest, design: ResumeDesignV2): Block[] {
  const blocks: Block[] = []
  // DOM order is reading order: regions in manifest order (main content first in every template).
  for (const region of manifest.regions) {
    document.sections.forEach((section, sectionIndex) => {
      if (regionFor(section.key, manifest, design) !== region.id) return
      const parts = partsOf(section)
      parts.forEach((part, index) => {
        const next = parts[index + 1]
        const joined = part.type === 'timeline' && next?.type === 'timeline' && next.item === part.item
        const row = (part.type === 'skill' || part.type === 'list') && next !== undefined
        blocks.push({ id: `${section.key}:${partId(part)}`, region: region.id, section, sectionKey: section.key, withHeading: index === 0, sectionIndex, joined, row, part })
      })
    })
  }
  return blocks
}
