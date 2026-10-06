import assert from 'node:assert/strict'
import test from 'node:test'
import { projectResumeSection } from './resumeStructuredEntries.ts'

const content = {
  education: [{
    school: '河南科技学院',
    major: '数据科学与大数据技术',
    degree: '本科',
    startDate: '2026-02',
    endDate: '2026-09',
    location: '新乡',
    description: '系统学习数据结构、数据库原理和机器学习。',
  }],
  experiences: [{
    company: '某商业咨询团队', role: '业务支持实习生',
    startDate: '2025-03', endDate: '2025-06', location: '上海',
    description: '负责资料归档和进度跟踪。',
  }],
  projects: [{
    name: '校园奶茶消费行为调研', role: '组长',
    startDate: '2024-09', endDate: '2025-01', location: '新乡',
    description: '完成问卷设计和数据分析。',
  }],
  organizations: [{
    name: '数据科学社团', role: '负责人',
    startDate: '2023-09', endDate: '2024-06', location: '新乡',
    description: '组织技术分享。',
  }],
}

test('timeline records keep primary title and date in separate render fields', () => {
  const cases = [
    ['education', '河南科技学院', '数据科学与大数据技术 · 本科', '2026.02 - 2026.09'],
    ['experience', '某商业咨询团队', '业务支持实习生', '2025.03 - 2025.06'],
    ['projects', '校园奶茶消费行为调研', '组长', '2024.09 - 2025.01'],
    ['organizations', '数据科学社团', '负责人', '2023.09 - 2024.06'],
  ] as const

  for (const [key, primary, secondary, date] of cases) {
    const projection = projectResumeSection(content, key, 'YYYY_DOT_MM')
    assert.equal(projection.entries?.length, 1)
    assert.equal(projection.entries?.[0]?.primary, primary)
    assert.equal(projection.entries?.[0]?.secondary, secondary)
    assert.equal(projection.entries?.[0]?.date, date)
    assert.equal(projection.entries?.[0]?.location, key === 'experience' ? '上海' : '新乡')
  }
})

test('Chinese date design is applied without moving the date into the body copy', () => {
  const projection = projectResumeSection(content, 'education', 'YYYY_CN_MM')
  const entry = projection.entries?.[0]
  assert.equal(entry?.date, '2026年2月 - 2026年9月')
  assert.equal(entry?.primary, '河南科技学院')
  assert.doesNotMatch(entry?.description ?? '', /2026|河南科技学院/)
})
