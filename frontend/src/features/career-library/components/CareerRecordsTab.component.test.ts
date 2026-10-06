import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AppDatePicker from '@/shared/ui/AppDatePicker.vue'
import CareerRecordsTab from './CareerRecordsTab.vue'
import type { CareerOverview, CareerRecord } from '../types'

const api = vi.hoisted(() => ({ records: vi.fn(), availability: vi.fn(), update: vi.fn() }))
vi.mock('../services/careerLibraryApi', async (importOriginal) => ({
  ...await importOriginal<typeof import('../services/careerLibraryApi')>(),
  fetchCareerRecords: api.records,
  fetchCareerAiAvailability: api.availability,
  updateCareerRecord: api.update,
}))

const record: CareerRecord = {
  id: 'record-date-qa', type: 'PROJECT', title: '资料日期回归', organization: '合成资料',
  startDate: '2024-01-12', endDate: '2025-12-08', description: '保留已保存的日期精度。',
  payload: {}, pendingSupplement: false, sourceType: 'USER', status: 'ACTIVE', confirmed: true,
  sortOrder: 0, resumeReferenceCount: 0, version: 3, createdAt: '2026-09-06', updatedAt: '2026-09-06',
}
const overview: CareerOverview = {
  profileCompleteness: 0, missingItems: [], totalRecords: 1, inUseRecords: 0, outcomeRecords: 0,
  pendingRecords: 0, skillKeywords: 0, healthScore: 10, storageUsedBytes: 0, storageQuotaBytes: 1024,
  readyFiles: 0, processingFiles: 0, categoryCounts: {}, updatedAt: '2026-09-06',
}

beforeEach(() => {
  api.records.mockResolvedValue({ items: [record], total: 1, page: 0, size: 100 })
  api.availability.mockResolvedValue({ available: false, model: '' })
  api.update.mockResolvedValue(record)
})

async function editRecord() {
  const wrapper = mount(CareerRecordsTab, {
    props: { overview, q: '', type: '', status: 'ACTIVE', sort: 'RECENT', layout: 'list' },
    // Reka dialogs portal into document.body; keep the real Teleport so the form renders.
    attachTo: document.body,
  })
  await flushPromises()
  await wrapper.get('button[title="编辑"]').trigger('click')
  await flushPromises()
  return wrapper
}

describe('career record dates', () => {
  it('shows full stored dates in month controls without losing their original precision on save', async () => {
    const wrapper = await editRecord()
    const dates = wrapper.findAllComponents(AppDatePicker)
    expect(dates[0]!.props('modelValue')).toBe('2024-01')
    expect(dates[1]!.props('modelValue')).toBe('2025-12')
    document.querySelector<HTMLFormElement>('#career-record-form')!.dispatchEvent(new Event('submit', { cancelable: true }))
    await flushPromises()
    expect(api.update).toHaveBeenCalledWith(record.id, expect.objectContaining({
      startDate: '2024-01-12', endDate: '2025-12-08', expectedVersion: 3,
    }))
  })

  it('saves a newly chosen month while retaining the untouched date', async () => {
    const wrapper = await editRecord()
    wrapper.findAllComponents(AppDatePicker)[0]!.vm.$emit('update:modelValue', '2024-03')
    document.querySelector<HTMLFormElement>('#career-record-form')!.dispatchEvent(new Event('submit', { cancelable: true }))
    await flushPromises()
    expect(api.update).toHaveBeenCalledWith(record.id, expect.objectContaining({
      startDate: '2024-03', endDate: '2025-12-08',
    }))
  })
})
