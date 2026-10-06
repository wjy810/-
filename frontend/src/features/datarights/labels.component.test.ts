import { describe, expect, it } from 'vitest'
import { deletionProgressNote, exportDownloadDisabledReason, objectSubmitBlockedReason } from './labels'
import type { DeletionPreview, ExportView } from './types'

describe('data rights feedback', () => {
  it('keeps missing, unfinished and expired exports unavailable with actionable feedback', () => {
    expect(exportDownloadDisabledReason(null)).toBe('还没有导出记录。完成导出后即可下载。')
    expect(exportDownloadDisabledReason({ id: 'export', taskStatus: 'RUNNING', fileId: 'file' })).toContain('不能下载')
    expect(exportDownloadDisabledReason({ id: 'export', downloadExpired: true, fileId: 'file' })).toContain('已过期')
    expect(exportDownloadDisabledReason({ id: 'export', taskStatus: 'SUCCEEDED' })).toContain('暂不可下载')
  })

  it.each<Partial<ExportView>>([
    { downloadUrl: '/api/v1/data-rights/exports/export/download' },
    { fileId: 'file' },
    { downloadAvailable: true },
  ])('keeps each existing download channel available', channel => {
    expect(exportDownloadDisabledReason({ id: 'export', taskStatus: 'SUCCEEDED', ...channel })).toBeNull()
  })

  it('does not describe pending, restricted or failed deletion as completed', () => {
    for (const status of ['SUBMITTED', 'PROCESSING', 'PARTIALLY_RESTRICTED', 'FAILED']) {
      const note = deletionProgressNote({ id: 'deletion', status })
      expect(note).not.toContain('已处理完成')
      expect(note).not.toMatch(/后端|编排|接口/)
    }
    expect(deletionProgressNote({ id: 'deletion', status: 'COMPLETED' })).toContain('依法保留')
    expect(deletionProgressNote({ id: 'deletion', status: 'COMPLETED', scope: 'OBJECT' })).toContain('具体范围')
  })

  it('retains object deletion safeguards without exposing protocol flags', () => {
    const preview: DeletionPreview = { scope: 'OBJECT', targetId: 'record', canProceed: true }
    expect(objectSubmitBlockedReason(preview, 'CAREER_RECORD', 'record')).toBe('')
    for (const invalid of [null, { scope: 'ACCOUNT' }, { ...preview, canProceed: false },
      { ...preview, impacts: [{ relation: 'BLOCKING' }] }, { ...preview, targetId: 'other' }]) {
      const reason = objectSubmitBlockedReason(invalid, 'CAREER_RECORD', 'record')
      expect(reason).not.toBe('')
      expect(reason).not.toMatch(/scope=|canProceed|BLOCKING|假装|后端/)
    }
  })
})
