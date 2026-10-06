import assert from 'node:assert/strict'
import test from 'node:test'
import {
  IMPORT_TEXT_MIN,
  importContactFields,
  importDraftSummary,
  importFailureText,
  importTextIssue,
  meaningfulLength,
} from './textImport.ts'

test('pasted text must carry enough non-blank characters for the parser', () => {
  assert.equal(meaningfulLength(' 教 育 \n 经 历 '), 4)
  assert.equal(importTextIssue('   '), '请先粘贴简历文字。')
  assert.match(importTextIssue('教育经历 浙江大学'), /至少需要 80 个字/)
  assert.equal(importTextIssue('简'.repeat(IMPORT_TEXT_MIN)), '')
  assert.equal(importTextIssue(`${'简 '.repeat(IMPORT_TEXT_MIN - 1)}`), `内容太短，至少需要 80 个字（不含空格），现在是 ${IMPORT_TEXT_MIN - 1} 个。`)
})

test('parser failure codes become plain guidance, unknown codes a neutral retry hint', () => {
  assert.match(importFailureText('RESUME_STRUCTURE_INSUFFICIENT'), /板块标题/)
  assert.doesNotMatch(importFailureText('RESUME_STRUCTURE_INSUFFICIENT'), /RESUME_/)
  assert.equal(importFailureText('SOMETHING_ELSE'), '简历解析没有成功，请稍后重试。')
  assert.equal(importFailureText(null), '简历解析没有成功，请稍后重试。')
})

test('the draft summary counts only sections the parser actually found', () => {
  const summary = importDraftSummary({
    summary: '三年后端开发经验',
    education: [{ school: '浙江大学' }],
    experiences: [{ company: '某科技公司' }, { company: '另一家公司' }],
    projects: [],
    skills: [{ category: '专业技能', items: ['Java', 'SQL', 'Redis'] }],
    certificates: 'not-a-list',
  })
  assert.deepEqual(summary.map((item) => [item.label, item.count]), [
    ['个人简介', 1], ['教育经历', 1], ['工作或实习经历', 2], ['技能', 3],
  ])
  assert.deepEqual(importDraftSummary(null), [])
})

test('recognised contact fields are listed by name', () => {
  assert.deepEqual(importContactFields({ basics: { name: '张三', email: '', phone: '13800000000' } }), ['姓名', '手机号'])
  assert.deepEqual(importContactFields({}), [])
})
