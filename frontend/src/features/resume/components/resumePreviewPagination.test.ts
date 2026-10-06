import assert from 'node:assert/strict'
import test from 'node:test'
import {
  paginatePreviewColumns,
  type PreviewPaginationColumn,
  type PreviewPaginationSection,
} from './resumePreviewPagination.ts'

function section(key: string, value: string): PreviewPaginationSection {
  return { key, label: key, value, headingStyle: 'rule' }
}

function column(id: string, widthPercent: number, sections: PreviewPaginationSection[]): PreviewPaginationColumn {
  return { id, widthPercent, slotKeys: sections.map((item) => item.key), tone: 'PLAIN', sections }
}

test('maxPages is an upper bound and short content stays on one page', () => {
  const result = paginatePreviewColumns(
    [column('main', 100, [section('education', '示例大学 软件工程 2022.09-2026.06')])],
    { maxPages: 2, capacityUnits: 3400 },
  )

  assert.equal(result.length, 1)
  assert.deepEqual(result[0]?.[0]?.sections.map((item) => item.key), ['education'])
})

test('long content is split without exceeding the template page limit', () => {
  const result = paginatePreviewColumns(
    [column('main', 100, [section('experience', '负责平台工程与稳定性交付。'.repeat(320))])],
    { maxPages: 2, capacityUnits: 3400 },
  )

  assert.equal(result.length, 2)
  assert.ok((result[0]?.[0]?.sections[0]?.value.length ?? 0) > 0)
  assert.ok((result[1]?.[0]?.sections[0]?.value.length ?? 0) > 0)
  assert.match(result[1]?.[0]?.sections[0]?.label ?? '', /（续）$/)
})

test('continuation pages fill the first sheet before moving remaining text', () => {
  const result = paginatePreviewColumns(
    [column('main', 100, [
      section('summary', '摘要内容'.repeat(70)),
      section('honors', '荣誉说明'.repeat(180)),
    ])],
    { maxPages: 2, capacityUnits: 900 },
  )

  assert.equal(result.length, 2)
  assert.deepEqual(result[0]?.[0]?.sections.map((item) => item.key), ['summary', 'honors'])
  assert.equal(result[1]?.[0]?.sections[0]?.key, 'honors')
  assert.match(result[1]?.[0]?.sections[0]?.label ?? '', /（续）$/)
})

test('multi-column continuation pages remove empty companion columns and use the full sheet width', () => {
  const result = paginatePreviewColumns([
    column('main', 70, [section('experience', 'Platform delivery '.repeat(300))]),
    column('facts', 30, [section('education', 'Computer Science')]),
  ], { maxPages: 2, capacityUnits: 3500 })

  assert.equal(result.length, 2)
  assert.equal(result[0]?.length, 2)
  assert.equal(result[1]?.length, 1)
  assert.equal(result[0]?.[1]?.sections[0]?.key, 'education')
  assert.equal(result[1]?.[0]?.id, 'main')
  assert.equal(result[1]?.[0]?.widthPercent, 100)
})

test('content that fits one sheet is rebalanced out of an overloaded narrow column', () => {
  const input = [
    column('main', 70, [
      section('summary', 'S'.repeat(200)),
      section('experience', 'E'.repeat(400)),
    ]),
    column('facts', 30, [
      section('skills', 'K'.repeat(300)),
      section('education', 'D'.repeat(300)),
      section('certificates', 'C'.repeat(250)),
      section('organizations', 'O'.repeat(250)),
    ]),
  ]

  const result = paginatePreviewColumns(input, { maxPages: 1, capacityUnits: 3000 })
  const keys = result[0]?.flatMap((item) => item.sections.map((entry) => entry.key)) ?? []
  const mainKeys = result[0]?.find((item) => item.id === 'main')?.sections.map((item) => item.key) ?? []
  const factsKeys = result[0]?.find((item) => item.id === 'facts')?.sections.map((item) => item.key) ?? []

  assert.equal(result.length, 1)
  assert.deepEqual([...keys].sort(), [
    'certificates', 'education', 'experience', 'organizations', 'skills', 'summary',
  ])
  assert.ok(mainKeys.includes('organizations') || mainKeys.includes('certificates'))
  assert.ok(factsKeys.length < input[1]!.sections.length)
})

test('structured student content uses both columns before creating page two', () => {
  const withBreaks = (key: string, length: number, lineBreaks: number) => {
    const characters = Array.from('内容'.repeat(Math.ceil(length / 2))).slice(0, length - lineBreaks)
    for (let index = 0; index < lineBreaks; index += 1) {
      characters.splice(Math.floor((index + 1) * characters.length / (lineBreaks + 1)), 0, '\n')
    }
    return section(key, characters.join(''))
  }
  const result = paginatePreviewColumns([
    column('profile', 36, [
      withBreaks('summary', 140, 0), withBreaks('education', 188, 4),
      withBreaks('skills', 427, 23), withBreaks('certificates', 243, 4),
    ]),
    column('growth', 64, [
      withBreaks('projects', 215, 4), withBreaks('experience', 193, 4),
      withBreaks('organizations', 174, 4), withBreaks('honors', 499, 10),
      withBreaks('languages', 153, 3),
    ]),
  ], { maxPages: 2, capacityUnits: 3300, density: 'COMPACT', lineHeight: 'COMPACT' })

  assert.equal(result.length, 1)
  assert.ok(result[0]?.find((item) => item.id === 'growth')?.sections.some((item) => item.key === 'certificates'))
})

test('a dense single-column resume continues instead of being visually clipped', () => {
  const result = paginatePreviewColumns(
    [column('main', 100, [section('all-sections', '结构化简历内容'.repeat(460))])],
    { maxPages: 2, capacityUnits: 3300, density: 'COMPACT', lineHeight: 'COMPACT' },
  )

  assert.equal(result.length, 2)
  assert.match(result[1]?.[0]?.sections[0]?.label ?? '', /（续）$/)
})

test('one-page templates never create a continuation page', () => {
  const result = paginatePreviewColumns(
    [column('main', 100, [section('experience', 'Long content '.repeat(1000))])],
    { maxPages: 1, capacityUnits: 300 },
  )

  assert.equal(result.length, 1)
})

test('structured timeline entries paginate only at entry boundaries without loss', () => {
  const entries = Array.from({ length: 6 }, (_, index) => ({
    primary: `公司 ${index + 1}`,
    secondary: '后端工程师',
    date: `202${index}.01 - 202${index}.12`,
    location: '杭州',
    description: `负责第 ${index + 1} 条经历的稳定交付。`.repeat(12),
    highlights: [`完成第 ${index + 1} 项验证`],
  }))
  const structured: PreviewPaginationSection = {
    ...section('experience', entries.map((entry) => Object.values(entry).join(' ')).join('\n\n')),
    entries,
  }

  const result = paginatePreviewColumns(
    [column('main', 100, [structured])],
    { maxPages: 2, capacityUnits: 900 },
  )
  const rendered = result.flatMap((page) => page[0]?.sections ?? []).flatMap((item) => item.entries ?? [])

  assert.equal(result.length, 2)
  assert.deepEqual(rendered.map((entry) => entry.primary), entries.map((entry) => entry.primary))
  assert.match(result[1]?.[0]?.sections[0]?.label ?? '', /（续）$/)
})
