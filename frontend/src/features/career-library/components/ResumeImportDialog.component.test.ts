import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({ listResumes: vi.fn(), candidates: vi.fn(), importRecords: vi.fn() }))
vi.mock('@/features/resume/services/resumeApi', () => ({ listResumes: api.listResumes }))
vi.mock('../services/careerLibraryApi', () => ({
  fetchResumeImportCandidates: api.candidates,
  importRecordsFromResume: api.importRecords,
}))

import ResumeImportDialog from './ResumeImportDialog.vue'

const stubs = {
  UiDialog: { props: ['open', 'title'], template: '<div v-if="open" role="dialog"><slot /><slot name="footer" /></div>' },
  UiSelect: true,
}

beforeEach(() => {
  api.listResumes.mockResolvedValue([{ id: 'm1', title: '数据分析简历', status: 'DRAFT', version: 1, updatedAt: null }])
  api.candidates.mockResolvedValue({
    masterId: 'm1',
    resumeTitle: '数据分析简历',
    items: [
      { key: 'experience-a', type: 'EXPERIENCE', title: '数据分析实习生', organization: '知味外卖', role: '数据分析实习生', startDate: '2025-06', endDate: '2025-09', alreadyInLibrary: false },
      { key: 'skill-b', type: 'SKILL', title: '数据', description: 'Python、SQL', alreadyInLibrary: true },
      { key: 'honor-c', type: 'HONOR', title: '一等学业奖学金', startDate: '2025-10', alreadyInLibrary: false },
    ],
  })
  api.importRecords.mockResolvedValue({ created: 1, skipped: 0, records: [] })
})

describe('ResumeImportDialog', () => {
  it('ticks only the new entries, keeps library entries locked, and sends the chosen keys', async () => {
    const wrapper = mount(ResumeImportDialog, { props: { open: true }, global: { stubs } })
    await flushPromises()
    expect(api.candidates).toHaveBeenCalledWith('m1')
    const text = wrapper.text()
    expect(text).toContain('工作经历')
    expect(text).toContain('专业技能')
    expect(text).toContain('已在资料库')
    expect(text).toContain('知味外卖 · 2025.06 – 2025.09')
    expect(text).not.toMatch(/EXPERIENCE|SKILL|HONOR/)
    const submit = wrapper.findAll('button').find(button => button.text().includes('到资料库'))!
    expect(submit.text()).toBe('添加 2 项到资料库')

    await wrapper.findAll('.rid__row').find(row => row.text().includes('一等学业奖学金'))!.trigger('click')
    expect(submit.text()).toBe('添加 1 项到资料库')
    await submit.trigger('click')
    await flushPromises()
    expect(api.importRecords).toHaveBeenCalledWith('m1', ['experience-a'])
    expect(wrapper.emitted('imported')?.[0]).toEqual([{ created: 1, skipped: 0, records: [] }])
  })
})
