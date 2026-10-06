import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '@/shared/api/types'

const api = vi.hoisted(() => ({ profile: vi.fn(), overview: vi.fn() }))
const router = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }))
const route = vi.hoisted(() => ({ value: null as unknown as { query: Record<string, string> } }))
vi.mock('../services/careerLibraryApi', async (importOriginal) => ({
  ...await importOriginal<typeof import('../services/careerLibraryApi')>(),
  fetchCareerProfile: api.profile,
  fetchCareerOverview: api.overview,
}))
vi.mock('vue-router', () => ({
  useRoute: () => route.value,
  useRouter: () => router,
  RouterLink: { name: 'RouterLink', props: ['to'], template: '<a :data-to="to"><slot /></a>' },
}))

import CareerLibraryPage from './CareerLibraryPage.vue'

const RecordsTabStub = { name: 'CareerRecordsTab', emits: ['remove'], template: '<div class="records-stub" />', methods: { openCreate() {} } }
const RouterLinkStub = { name: 'RouterLink', props: ['to'], template: '<a :data-to="to"><slot /></a>' }
const stubs = { UiIllustration: true, CareerLibraryTabs: true, CareerProfileTab: true, CareerFilesTab: true, CareerRecordsTab: RecordsTabStub, RouterLink: RouterLinkStub }

beforeEach(() => {
  route.value = reactive({ query: { view: 'records' } })
  api.profile.mockResolvedValue({ basics: {}, intentions: {}, preferences: {}, version: 1, updatedAt: '2026-09-01', completeness: 50, missingItems: [] })
  api.overview.mockResolvedValue({ missingItems: [] })
})

describe('CareerLibraryPage', () => {
  it('shows a retryable error and no dead header actions when the library fails to load', async () => {
    api.overview.mockRejectedValueOnce(new ApiClientError({ category: 'SYSTEM_FAILURE', reason: 'X', message: '服务暂时不可用' }, 503, 'req-lib'))
    const wrapper = mount(CareerLibraryPage, { global: { stubs } })
    await flushPromises()
    expect(wrapper.text()).toContain('求职资料库读取失败')
    expect(wrapper.text()).toContain('req-lib')
    expect(wrapper.find('.career-head-actions').exists()).toBe(false)
  })

  it('names the upload honestly and sends permanent deletion to the data-rights page', async () => {
    const wrapper = mount(CareerLibraryPage, { global: { stubs } })
    await flushPromises()
    expect(wrapper.text()).toContain('上传简历文件')
    expect(wrapper.text()).not.toContain('导入简历')
    expect(wrapper.text()).toContain('不会自动拆分成经历记录')
    wrapper.getComponent(RecordsTabStub).vm.$emit('remove', { type: 'CAREER_RECORD', id: 'record-1' })
    expect(router.push).toHaveBeenCalledWith({ path: '/account/data-rights', query: { targetType: 'CAREER_RECORD', targetId: 'record-1' } })
  })

  it('links the privacy action to the data-rights page', async () => {
    route.value.query = { view: 'profile' }
    const wrapper = mount(CareerLibraryPage, { global: { stubs } })
    await flushPromises()
    expect(wrapper.get('.privacy-button').attributes('data-to')).toBe('/account/data-rights')
  })
})
