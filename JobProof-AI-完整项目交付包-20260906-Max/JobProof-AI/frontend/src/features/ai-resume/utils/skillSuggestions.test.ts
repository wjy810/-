import assert from 'node:assert/strict'
import test from 'node:test'
import { canonicalSkillCategory, mergeSkillGroups } from './skillSuggestions.ts'

const group = (category: string, items: string[], description: string) => ({
  category, items, description, sourceRefs: [], verificationRequired: true, verificationItems: [],
})

test('skill groups replace the empty draft and preserve structured descriptions', () => {
  assert.deepEqual(
    mergeSkillGroups([{ category: '', items: [], description: '' }], [group('后端开发', ['Java'], '真实描述')]),
    [{ category: '后端开发', items: ['Java'], description: '真实描述' }],
  )
})

test('skill groups merge category aliases without replacing existing skills or descriptions', () => {
  assert.deepEqual(
    mergeSkillGroups(
      [{ category: '后端开发', items: ['Java'], description: '用户原有描述' }],
      [group('后端框架', ['java', 'Spring Boot'], '• AI 新增说明')],
    ),
    [{ category: '后端开发', items: ['Java', 'Spring Boot'], description: '• 用户原有描述\n• AI 新增说明' }],
  )
})

test('skill groups merge database aliases instead of adding a duplicate category', () => {
  assert.deepEqual(
    mergeSkillGroups([{ category: '数据库', items: ['MySQL'] }], [group('数据库与存储', ['Redis'], '• 存储说明')]),
    [{ category: '数据库与存储', items: ['MySQL', 'Redis'], description: '• 存储说明' }],
  )
})

test('skill groups append genuinely distinct categories in generated order', () => {
  assert.deepEqual(
    mergeSkillGroups([{ category: '数据库', items: ['MySQL'] }], [group('工程工具', ['Git'], '• 工具说明')]),
    [
      { category: '数据库', items: ['MySQL'] },
      { category: '工程工具', items: ['Git'], description: '• 工具说明' },
    ],
  )
})

test('skill category aliases are normalized to the stable taxonomy', () => {
  assert.equal(canonicalSkillCategory('构建与依赖管理'), '工程工具')
  assert.equal(canonicalSkillCategory('关系型数据库'), '数据库与存储')
  assert.equal(canonicalSkillCategory('关系型数据库与持久层'), '数据库与存储')
  assert.equal(canonicalSkillCategory('持久层框架'), '后端开发')
})
