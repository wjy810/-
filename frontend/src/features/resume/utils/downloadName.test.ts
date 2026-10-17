import assert from 'node:assert/strict'
import test from 'node:test'
import { resumeFileName } from './downloadName.ts'

test('names a resume after the person and the target job', () => {
  assert.equal(resumeFileName({ name: '周以宁', target: '数据分析师', extension: 'pdf' }), '周以宁-数据分析师-简历.pdf')
  assert.equal(resumeFileName({ name: '周以宁', extension: 'docx' }), '周以宁-简历.docx')
})

test('the anonymous copy leaves the name out', () => {
  assert.equal(resumeFileName({ name: '周以宁', target: '数据分析师', anonymous: true, extension: 'pdf' }), '数据分析师-匿名简历.pdf')
  assert.equal(resumeFileName({ name: '周以宁', anonymous: true, extension: 'pdf' }), '匿名简历.pdf')
})

test('falls back to the resume title, then to a plain name', () => {
  assert.equal(resumeFileName({ title: '秋招 · 后端', extension: 'pdf' }), '秋招 · 后端.pdf')
  assert.equal(resumeFileName({ extension: 'pdf' }), '简历.pdf')
})

test('strips characters file systems reject and caps the length', () => {
  assert.equal(resumeFileName({ name: 'A/B', target: 'C:D*E?', extension: 'pdf' }), 'A B-C D E-简历.pdf')
  assert.equal(Array.from(resumeFileName({ title: '长'.repeat(200), extension: 'pdf' })).length, 84)
})
