import assert from 'node:assert/strict'
import test from 'node:test'
import {
  descriptionFactsFingerprint,
  descriptionInputIssue,
  descriptionSuggestionKey,
  mergeDescription,
} from './descriptionSuggestion.ts'

test('description suggestion keys isolate cards and record indexes', () => {
  assert.equal(descriptionSuggestionKey('education-card', 2), 'education-card:2')
  assert.notEqual(descriptionSuggestionKey('education-card', 1), descriptionSuggestionKey('experience-card', 1))
})

test('fact fingerprint is stable across object key order', () => {
  assert.equal(
    descriptionFactsFingerprint({ school: '示例大学', major: '软件工程' }),
    descriptionFactsFingerprint({ major: '软件工程', school: '示例大学' }),
  )
})

test('description candidate can append or explicitly replace without duplication', () => {
  assert.equal(mergeDescription('', '参与课程设计。', 'append'), '参与课程设计。')
  assert.equal(mergeDescription('原有描述。', '候选描述。', 'append'), '原有描述。\n候选描述。')
  assert.equal(mergeDescription('原有描述。', '候选描述。', 'replace'), '候选描述。')
  assert.equal(mergeDescription('已有候选描述。', '候选描述。', 'append'), '已有候选描述。')
})

test('multi-line resume bullets remain intact when applied', () => {
  const bullets = '• 参与课程项目并完成数据建模。\n• 使用 Spark 处理已确认的数据。'
  assert.equal(mergeDescription('', bullets, 'replace'), bullets)
  assert.equal(mergeDescription('主修数据库课程。', bullets, 'append'), `主修数据库课程。\n${bullets}`)
})

test('all supported record types allow AI reference drafts from semantic header fields', () => {
  assert.equal(
    descriptionInputIssue('EXPERIENCE', { company: '星火小组', role: '全栈', location: '新乡' }),
    '',
  )
  assert.equal(descriptionInputIssue('PROJECTS', {
    name: '数据平台',
  }), '')
  assert.equal(descriptionInputIssue('ORGANIZATIONS', { name: '学生会', role: '成员' }), '')
  assert.equal(descriptionInputIssue('EDUCATION', { school: '示例大学' }), '')
  assert.equal(descriptionInputIssue('LANGUAGES', { language: '英语', level: '熟练' }), '')
  assert.equal(descriptionInputIssue('EXPERIENCE', { description: '负责接口开发' }), '')
  assert.match(descriptionInputIssue('EXPERIENCE', { startDate: '2026-01', location: '新乡' }), /公司.*岗位/)
  assert.match(descriptionInputIssue('PROJECTS', {}), /项目名称、角色或补充描述/)
  assert.match(descriptionInputIssue('LANGUAGES', { level: '' }), /选择一种语言/)
})
