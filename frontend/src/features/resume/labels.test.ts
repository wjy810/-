import assert from 'node:assert/strict'
import test from 'node:test'
import {
  glossMasterStatus,
  glossVersionStatus,
  masterSourceLabel,
  pdfDownloadDisabledReason,
  versionOrdinals,
  versionSourceLabel,
} from './labels.ts'

test('versions are numbered by creation order, not by their lock counter', () => {
  const ordinals = versionOrdinals([
    { id: 'c', createdAt: '2026-09-03T08:00:00Z', frozenAt: null },
    { id: 'a', createdAt: '2026-09-01T08:00:00Z', frozenAt: null },
    { id: 'b', createdAt: null, frozenAt: '2026-09-02T08:00:00Z' },
  ])
  assert.deepEqual([...ordinals.entries()], [['a', 1], ['b', 2], ['c', 3]])
})

test('sources and statuses never surface raw enum names', () => {
  assert.equal(versionSourceLabel('AI_WORKBENCH_PDF'), '工作台导出 PDF')
  assert.equal(versionSourceLabel('SOMETHING_NEW'), '冻结版本')
  assert.equal(masterSourceLabel('IMPORT'), '导入')
  assert.equal(masterSourceLabel('LEGACY_X'), '—')
  assert.equal(glossMasterStatus('MYSTERY'), '未知状态')
  assert.equal(glossVersionStatus('FROZEN'), '已冻结')
})

test('download reasons are plain sentences without task enums or field names', () => {
  assert.match(pdfDownloadDisabledReason({ status: 'RUNNING' }) ?? '', /正在生成/)
  assert.doesNotMatch(pdfDownloadDisabledReason({ status: 'RUNNING' }) ?? '', /RUNNING/)
  assert.doesNotMatch(pdfDownloadDisabledReason({ status: 'SUCCEEDED', fileId: '' }) ?? '', /fileId/)
  assert.equal(pdfDownloadDisabledReason({ status: 'SUCCEEDED', fileId: 'file-1' }), null)
})
