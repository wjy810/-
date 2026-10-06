import assert from 'node:assert/strict'
import test from 'node:test'
import { buildBlocks } from './blocks.ts'

const rich = (bullets: string[]) => ({ paragraphs: [], bullets: bullets.map(text => [{ text }]) })
const manifest = { regions: [{ id: 'main', sections: ['experience', 'skills'] }] } as never
const design = { regionAssignments: {} } as never

test('split entries are joined until their last part; rows are compact until the section ends', () => {
  const document = {
    locale: 'zh-CN',
    header: {},
    sections: [
      {
        kind: 'timeline', key: 'experience', title: '工作经历',
        items: [
          { id: 'a', title: 'A', subtitle: '', location: '', dates: '', start: '', end: '', body: rich(['1', '2', '3', '4', '5', '6']) },
          { id: 'b', title: 'B', subtitle: '', location: '', dates: '', start: '', end: '', body: rich(['1']) },
        ],
      },
      {
        kind: 'skills', key: 'skills', title: '技能',
        groups: [1, 2, 3].map(index => ({ id: `g${index}`, name: `G${index}`, items: ['x'], body: rich([]) })),
      },
    ],
  } as never
  const blocks = buildBlocks(document, manifest, design)
  const experience = blocks.filter(block => block.sectionKey === 'experience')
  // Entry A: head (bullets 1–2) + four single-bullet parts; entry B: one part.
  assert.deepEqual(experience.map(block => block.joined), [true, true, true, true, false, false])
  assert.deepEqual(experience.map(block => block.row), [false, false, false, false, false, false])
  assert.deepEqual(experience.map(block => block.withHeading), [true, false, false, false, false, false])
  const skills = blocks.filter(block => block.sectionKey === 'skills')
  assert.deepEqual(skills.map(block => block.row), [true, true, false])
})
