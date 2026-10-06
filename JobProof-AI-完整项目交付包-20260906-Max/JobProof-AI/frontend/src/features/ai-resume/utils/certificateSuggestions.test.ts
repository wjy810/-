import assert from 'node:assert/strict'
import test from 'node:test'
import {
  hasCertificateFacts,
  credentialDescriptionCharacters,
  credentialDescriptionIssue,
  isCredentialFactsInsufficientReason,
  mergeCredentialSuggestions,
  mergeCredentialNameCandidates,
  mergeCertificateSuggestions,
  normalizeCertificateName,
} from './certificateSuggestions.ts'

const suggestion = (name: string, issuer = '', date = '', description = '') => ({
  name, issuer, date, description, sourceRefs: [], verificationRequired: true, verificationItems: [],
})

test('certificate suggestions replace the empty placeholder with generated entries', () => {
  assert.deepEqual(
    mergeCertificateSuggestions(
      [{ name: '', issuer: '', date: '', description: '' }],
      [suggestion('软件设计师', '工业和信息化部', '2025-05', '已取得资格')],
    ),
    [{ name: '软件设计师', issuer: '工业和信息化部', date: '2025-05', description: '已取得资格' }],
  )
})

test('same certificate keeps existing fields and only fills missing values', () => {
  assert.deepEqual(
    mergeCertificateSuggestions(
      [{ name: 'PMP 认证', issuer: '用户原机构', date: '', description: '用户原说明' }],
      [suggestion('PMP®', 'AI 机构', '2024-08', '• AI 补充说明')],
    ),
    [{
      name: 'PMP 认证', issuer: '用户原机构', date: '2024-08',
      description: '用户原说明\n• AI 补充说明',
    }],
  )
})

test('same certificate preserves the exact existing description and appends only new lines', () => {
  assert.deepEqual(
    mergeCertificateSuggestions(
      [{ name: '软件设计师', issuer: '', date: '', description: '• 用户原说明' }],
      [suggestion('软件设计师', '', '', '用户原说明\n• AI 新说明')],
    ),
    [{ name: '软件设计师', issuer: '', date: '', description: '• 用户原说明\n• AI 新说明' }],
  )
})

test('different certificates append without deleting existing entries', () => {
  assert.deepEqual(
    mergeCertificateSuggestions(
      [{ name: 'CET-6', issuer: '教育部考试中心', date: '2023-06', description: '' }],
      [suggestion('软件设计师', '', '', '')],
    ).map((item) => item.name),
    ['CET-6', '软件设计师'],
  )
})

test('certificate name normalization handles punctuation, spaces, and common suffixes', () => {
  assert.equal(normalizeCertificateName('PMP® 认证'), normalizeCertificateName('PMP'))
  assert.equal(normalizeCertificateName('CET-6'), normalizeCertificateName('CET 6'))
})

test('blank certificate placeholders are not treated as extractable facts', () => {
  assert.equal(hasCertificateFacts([{ name: '', issuer: '', date: '', description: '' }]), false)
  assert.equal(hasCertificateFacts([{ name: 'CET-6', issuer: '', date: '', description: '' }]), true)
})

test('empty certificate and honor extraction can fall back to AI recommendations', () => {
  assert.equal(isCredentialFactsInsufficientReason('AI_CERTIFICATE_FACTS_INSUFFICIENT'), true)
  assert.equal(isCredentialFactsInsufficientReason('AI_HONOR_FACTS_INSUFFICIENT'), true)
  assert.equal(isCredentialFactsInsufficientReason('AI_CHANNEL_UNAVAILABLE'), false)
})

test('credential descriptions count only meaningful letters and numbers', () => {
  assert.equal(credentialDescriptionCharacters('• 证书 123，已取得。'), 8)
  assert.match(
    credentialDescriptionIssue([{ name: '软件设计师', issuer: '测试机构', description: '说明太短' }], '证书与资质'),
    /至少需要 60 个有效字符/u,
  )
  assert.equal(
    credentialDescriptionIssue([{
      name: '软件设计师',
      issuer: '工业和信息化部',
      description: '已确认该证书真实取得，补充说明围绕考核范围、取得过程、实践内容和可核实成果展开，机构、日期与结果均来自用户确认事实，不增加任何未经确认的信息。',
    }], '证书与资质'),
    '',
  )
})

test('credential entries require an issuer before they can be submitted', () => {
  assert.match(
    credentialDescriptionIssue([{
      name: '全国计算机等级考试二级',
      issuer: '',
      description: '已确认取得该证书，补充说明围绕考核范围、学习过程、知识内容和实际应用方向展开，所有信息都需要由用户核对确认，不增加未经确认的成绩、编号或日期。',
    }], '证书与资质'),
    /颁发机构/u,
  )
})

test('honor suggestions preserve same-name facts and append different honors', () => {
  assert.deepEqual(
    mergeCredentialSuggestions(
      [{ name: '优秀学生干部荣誉', issuer: '原机构', date: '', description: '原说明' }],
      [suggestion('优秀学生干部', 'AI 机构', '2024-10', '• AI 补充说明'), suggestion('国家奖学金')],
      'HONOR',
    ),
    [
      { name: '优秀学生干部荣誉', issuer: '原机构', date: '2024-10', description: '原说明\n• AI 补充说明' },
      { name: '国家奖学金', issuer: '', date: '', description: '' },
    ],
  )
})

test('owned candidates replace duplicate AI recommendations and multiple recommendations remain selectable', () => {
  const recommendation = (name: string) => ({
    name, reason: '根据目标岗位和技能方向推荐', candidateType: 'RECOMMENDED' as const,
    sourceRefs: [{ key: 'resume.skills.0', label: '专业技能 1', excerpt: 'Java、Spring Boot' }],
  })
  const owned = {
    name: '软件设计师资格', reason: '来自当前证书草稿', candidateType: 'OWNED' as const,
    sourceRefs: [{ key: 'draft.0', label: '当前证书草稿', excerpt: '软件设计师资格' }],
  }
  const merged = mergeCredentialNameCandidates(
    [recommendation('软件设计师资格'), recommendation('PMP')], [owned], 'CERTIFICATE',
  )
  assert.equal(merged.length, 2)
  assert.equal(merged[0].candidateType, 'OWNED')
  assert.equal(merged[0].sourceRefs[0].key, 'draft.0')
})
