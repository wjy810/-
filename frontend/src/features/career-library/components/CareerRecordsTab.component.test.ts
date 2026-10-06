import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '@/shared/api/types'
import AppDatePicker from '@/shared/ui/AppDatePicker.vue'
import UiDropdownMenu, { type MenuItem } from '@/shared/ui/UiDropdownMenu.vue'
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

async function mountTab() {
  const wrapper = mount(CareerRecordsTab, {
    props: { overview, q: '', type: '', status: 'ACTIVE', sort: 'RECENT', layout: 'list' },
    // Reka dialogs portal into document.body; keep the real Teleport so the form renders.
    attachTo: document.body,
    global: { stubs: { UiIllustration: true } },
  })
  await flushPromises()
  return wrapper
}

async function editRecord() {
  const wrapper = await mountTab()
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

describe('career records list', () => {
  it('shows a retryable error, not the empty state, when the list fails to load', async () => {
    api.records.mockRejectedValueOnce(new ApiClientError({ category: 'SYSTEM_FAILURE', reason: 'X', message: '服务暂时不可用' }, 503, 'req-records'))
    const wrapper = await mountTab()
    expect(wrapper.text()).toContain('经历资料读取失败')
    expect(wrapper.text()).toContain('req-records')
    expect(wrapper.text()).not.toContain('还没有符合条件的经历')
    await wrapper.findAll('button').find(button => button.text().includes('重试'))!.trigger('click')
    await flushPromises()
    expect(wrapper.find('.timeline-record').exists()).toBe(true)
  })

  it('offers "加载更多" when the server has more records than the first page', async () => {
    api.records.mockResolvedValueOnce({ items: [record], total: 2, page: 0, size: 50 })
      .mockResolvedValueOnce({ items: [{ ...record, id: 'record-2', title: '第二条' }], total: 2, page: 1, size: 50 })
    const wrapper = await mountTab()
    expect(wrapper.text()).toContain('已显示 1 / 2 条')
    await wrapper.findAll('button').find(button => button.text() === '加载更多')!.trigger('click')
    await flushPromises()
    expect(api.records).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }))
    expect(wrapper.findAll('.timeline-record')).toHaveLength(2)
    expect(wrapper.text()).not.toContain('加载更多')
  })

  it('routes "永久删除" from the more-actions menu to the data-rights flow', async () => {
    const wrapper = await mountTab()
    const items = wrapper.getComponent(UiDropdownMenu).props('items') as MenuItem[]
    const remove = items.find(item => 'label' in item && item.label === '永久删除…') as Extract<MenuItem, { onSelect: () => void }>
    remove.onSelect()
    expect(wrapper.emitted('remove')).toEqual([[{ type: 'CAREER_RECORD', id: record.id }]])
  })

  it('wires each to-complete tip to a real action', async () => {
    api.records.mockResolvedValue({ items: [{ ...record, coreOutcome: '' }], total: 1, page: 0, size: 50 })
    const wrapper = await mountTab()
    const tip = (label: string) => wrapper.findAll('.record-tips button').find(button => button.text().includes(label))!
    expect(tip('补全经历时间').attributes('disabled')).toBeDefined()
    await tip('上传证明材料').trigger('click')
    expect(wrapper.emitted('uploadFile')).toEqual([['CERTIFICATE']])
    await tip('补充可量化的成果').trigger('click')
    await flushPromises()
    expect(document.querySelector('#record-core-outcome')).not.toBeNull()
  })
})
