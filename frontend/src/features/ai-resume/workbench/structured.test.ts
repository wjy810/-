import assert from 'node:assert/strict'
import { test } from 'node:test'
import { blankStructuredItem, cleanItems, itemsOf, parseSkillItems, recordHeadline, reorder, seedPayloadFromContent } from './structured.ts'

test('itemsOf always yields at least one blank record of the right shape', () => {
  assert.deepEqual(itemsOf('EDUCATION', undefined), [blankStructuredItem('EDUCATION')])
  assert.deepEqual(itemsOf('SKILLS', { items: [] }), [{ category: '', items: [], description: '' }])
  const items = [{ school: 'A' }]
  assert.equal(itemsOf('EDUCATION', { items })[0], items[0])
})

test('cleanItems drops records whose every field is blank', () => {
  assert.deepEqual(cleanItems([blankStructuredItem('EXPERIENCE'), { company: '甲', current: false }, { items: ['Java'] }, 'x']), [
    { company: '甲', current: false },
    { items: ['Java'] },
  ])
  assert.deepEqual(cleanItems(null), [])
})

test('reorder moves one element and ignores out-of-range moves', () => {
  assert.deepEqual(reorder(['a', 'b', 'c', 'd'], 0, 2), ['b', 'c', 'a', 'd'])
  assert.deepEqual(reorder(['a', 'b', 'c'], 2, 0), ['c', 'a', 'b'])
  assert.deepEqual(reorder(['a', 'b'], 0, 5), ['a', 'b'])
})

test('skill parsing accepts Chinese and English separators', () => {
  assert.deepEqual(parseSkillItems('Java、Spring Boot, MySQL，Redis\nGit,, '), ['Java', 'Spring Boot', 'MySQL', 'Redis', 'Git'])
})

test('record headlines summarise a record for collapsed rows', () => {
  assert.equal(recordHeadline('EDUCATION', { school: '浙江大学', major: '计算机' }), '浙江大学 · 计算机')
  assert.equal(recordHeadline('PROJECTS', { name: '交易平台', role: '' }), '交易平台')
  assert.equal(recordHeadline('LANGUAGES', { language: '英语', level: '熟练' }), '英语 · 熟练')
})

test('an empty card starts from the confirmed content, without the duplicated import highlights', () => {
  const content = {
    summary: '三年后端开发经验',
    basics: { name: '张三', email: 'a@example.test', phone: '' },
    experiences: [{ company: '某科技公司', description: '负责接口\n优化查询', highlights: ['负责接口', '优化查询'] }],
  }
  assert.deepEqual(seedPayloadFromContent('EXPERIENCE', { items: [] }, content), {
    items: [{ company: '某科技公司', description: '负责接口\n优化查询' }],
  })
  assert.deepEqual(seedPayloadFromContent('SUMMARY', { text: '' }, content), { text: '三年后端开发经验' })
  assert.deepEqual(seedPayloadFromContent('CONTACT', { name: '', email: '', phone: '', location: '', links: [] }, content),
    { name: '张三', email: 'a@example.test', phone: '', location: '', links: [] })
})

test('a card that already holds data, or content with nothing to offer, is left untouched', () => {
  const payload = { items: [{ company: '用户正在编辑的公司' }] }
  assert.equal(seedPayloadFromContent('EXPERIENCE', payload, { experiences: [{ company: '旧公司' }] }), payload)
  const blank = { items: [] }
  assert.equal(seedPayloadFromContent('EDUCATION', blank, { education: [] }), blank)
  assert.equal(seedPayloadFromContent('TARGET_JOB', { targetJob: '' }, { intentions: { targetJob: '后端' } }).targetJob, '')
  const kept = { highlights: ['A'], description: '另写的描述' }
  assert.deepEqual(seedPayloadFromContent('PROJECTS', {}, { projects: [kept] }), { items: [kept] })
})
