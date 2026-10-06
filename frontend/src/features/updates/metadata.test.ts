import assert from 'node:assert/strict'
import test from 'node:test'
import { buildUpdateMetadata } from './metadata.ts'
import type { ReleaseSummary } from './types.ts'

test('update list metadata uses the public canonical path and collection schema', () => {
  const metadata = buildUpdateMetadata()
  assert.equal(metadata.canonicalPath, '/updates')
  assert.equal(metadata.title, '系统更新日志 · JobProof AI')
  assert.equal(metadata.jsonLd['@type'], 'CollectionPage')
})

test('update detail metadata is derived from the published release', () => {
  const release: ReleaseSummary = {
    id: 'release-1', versionLabel: 'v2.4.0', slug: 'v2-4-0', title: '简历导出升级',
    summary: 'PDF 导出质量和分页能力获得提升。', releaseType: 'IMPROVEMENT', status: 'PUBLISHED',
    audience: 'PUBLIC', modules: ['AI_RESUME'], showWhatsNew: true, sendNotification: true,
    publishedAt: '2026-08-26T08:00:00Z', currentRevision: 1, versionNo: 2,
    createdAt: '2026-08-25T08:00:00Z', updatedAt: '2026-08-26T09:00:00Z', readCount: 0,
  }
  const metadata = buildUpdateMetadata(release)
  assert.equal(metadata.title, 'v2.4.0 简历导出升级 · JobProof AI')
  assert.equal(metadata.canonicalPath, '/updates/v2-4-0')
  assert.equal(metadata.jsonLd['@type'], 'TechArticle')
  assert.equal(metadata.jsonLd['datePublished'], release.publishedAt)
})
