import assert from 'node:assert/strict'
import test from 'node:test'
import {
  careerSkillCatalog, filterCareerSkillOptions, normalizeCareerSkillName,
  removeCareerSkill, upsertCareerSkill,
} from './skillPicker.ts'

test('career skills normalize whitespace and casing for stable identity', () => {
  assert.equal(normalizeCareerSkillName('  Spring   Boot '), 'spring boot')
  assert.equal(normalizeCareerSkillName('JAVA'), normalizeCareerSkillName('java'))
})

test('career skill selection changes status without creating a duplicate', () => {
  const practiced = upsertCareerSkill([], { name: 'Java', status: 'PRACTICED' })
  const learning = upsertCareerSkill(practiced, { name: ' java ', status: 'LEARNING' })
  assert.deepEqual(learning, [{ name: 'Java', status: 'LEARNING', custom: undefined }])
})

test('career skill catalog supports category and keyword filtering', () => {
  assert.deepEqual(
    filterCareerSkillOptions(careerSkillCatalog, 'spring', '技术研发').map(item => item.name),
    ['Spring Boot'],
  )
  assert.ok(filterCareerSkillOptions(careerSkillCatalog, '', '产品运营').every(item => item.category === '产品运营'))
})

test('custom career skills can be removed by normalized name', () => {
  const skills = upsertCareerSkill([], { name: '行业需求分析', status: 'PRACTICED', custom: true })
  assert.deepEqual(removeCareerSkill(skills, ' 行业需求分析 '), [])
})
