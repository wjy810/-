import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({ createApplication: vi.fn(), fetchShareableVersions: vi.fn(), updateApplication: vi.fn() }))
vi.mock('../services/applicationsApi', () => api)
const matches = vi.hoisted(() => ({ listJobMatches: vi.fn() }))
vi.mock('@/features/job-match/services/jobMatchApi', () => matches)
vi.mock('@/shared/ui/confirm', () => ({ confirm: vi.fn() }))
import ApplicationDialog from './ApplicationDialog.vue'

const stubs = {
  UiDialog: { props: ['open'], template: '<div v-if="open"><slot /><slot name="footer" /></div>' },
  UiSelect: {
    props: ['modelValue', 'options'],
    emits: ['update:modelValue'],
    template: '<select :aria-label="$attrs[\'aria-label\']" :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><option v-for="option in options" :key="option.value" :value="option.value">{{ option.label }}</option></select>',
  },
  AppDatePicker: { props: ['modelValue', 'label'], template: '<input class="date-stub" :aria-label="label" :value="modelValue">' },
}

function today(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
}

describe('ApplicationDialog 想投', () => {
  beforeEach(() => {
    Object.values(api).forEach(mock => mock.mockReset())
    api.fetchShareableVersions.mockResolvedValue([{ versionId: 'v1', masterId: 'm1', resumeTitle: '数据分析简历', ordinal: 2, anonymous: false, frozenAt: '2026-10-01T00:00:00Z' }])
    matches.listJobMatches.mockResolvedValue([])
    api.createApplication.mockImplementation(async (write: Record<string, unknown>) => ({ id: 'a1', ...write }))
  })

  it('records a wishlist entry with no applied date and no link, and dates it again when switched back', async () => {
    const wrapper = mount(ApplicationDialog, { props: { open: true }, global: { stubs } })
    await flushPromises()
    expect(wrapper.get('[aria-label="投递日期"]').element.getAttribute('value')).toBe(today())
    expect(wrapper.text()).toContain('生成这家公司的专属简历链接')

    await wrapper.get('select[aria-label="当前阶段"]').setValue('WISHLIST')
    expect(wrapper.find('[aria-label="投递日期"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('生成这家公司的专属简历链接')
    expect(wrapper.get('[data-testid="wishlist-note"]').text()).toContain('先不生成专属链接')

    const [company, position] = wrapper.findAll('input:not(.date-stub)')
    await company!.setValue('心愿公司')
    await position!.setValue('产品经理')
    await wrapper.get('#application-form').trigger('submit')
    await flushPromises()
    expect(api.createApplication).toHaveBeenCalledWith(expect.objectContaining({
      stage: 'WISHLIST', resumeVersionId: 'v1', createLink: false, appliedOn: null,
    }))

    await wrapper.get('select[aria-label="当前阶段"]').setValue('APPLIED')
    expect(wrapper.get('[aria-label="投递日期"]').element.getAttribute('value')).toBe(today())
  })
})
