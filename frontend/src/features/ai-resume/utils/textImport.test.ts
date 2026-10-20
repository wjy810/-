import assert from 'node:assert/strict'
import test from 'node:test'
import {
  IMPORT_TEXT_MIN,
  importContactFields,
  importDraftSummary,
  fileTaskFailureText,
  importFailureText,
  importFileIssue,
  importTextIssue,
  isImportableResumeFile,
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

test('a file import explains scans and file types instead of asking to paste more text', () => {
  assert.match(importFailureText('SCANNED_OR_EMPTY_RESUME', 'CAREER_FILE'), /扫描件/)
  assert.match(importFailureText('SCANNED_OR_EMPTY_RESUME', 'PASTED_TEXT'), /粘贴完整的简历内容/)
  assert.equal(importFailureText('OTHER', 'CAREER_FILE'), '简历解析没有成功，请稍后重试。')
  assert.match(fileTaskFailureText('CAREER_FILE_MALWARE_DETECTED'), /不安全/)
  assert.match(fileTaskFailureText('CAREER_FILE_QUOTA_EXCEEDED'), /空间已满/)
  assert.doesNotMatch(fileTaskFailureText('CAREER_FILE_PROCESSING_FAILED'), /CAREER_/)
})

test('only PDF and DOCX files up to 10 MB can be imported', () => {
  assert.equal(importFileIssue({ name: '简历.PDF', size: 2048 }), '')
  assert.equal(importFileIssue({ name: 'cv.docx', size: 10 * 1024 * 1024 }), '')
  assert.match(importFileIssue({ name: 'cv.doc', size: 2048 }), /另存为 \.docx/)
  assert.match(importFileIssue({ name: 'cv.png', size: 2048 }), /只能导入 PDF 或 Word/)
  assert.match(importFileIssue({ name: 'cv.pdf', size: 10 * 1024 * 1024 + 1 }), /10 MB/)
  assert.equal(importFileIssue(null), '请先选择简历文件。')
  assert.equal(isImportableResumeFile('application/pdf'), true)
  assert.equal(isImportableResumeFile('image/png'), false)
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
