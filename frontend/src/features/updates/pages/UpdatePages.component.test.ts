import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '@/shared/api/types'
import type { ReleaseDetail } from '../types'

const api = vi.hoisted(() => ({ list: vi.fn(), facets: vi.fn(), latest: vi.fn(), detail: vi.fn(), read: vi.fn() }))
const route = vi.hoisted(() => ({ value: null as unknown as { query: Record<string, string>; params: Record<string, string> } }))
vi.mock('../services/updatesApi', async (importOriginal) => ({
  ...await importOriginal<typeof import('../services/updatesApi')>(),
  listUpdates: api.list,
  fetchUpdateFacets: api.facets,
  fetchLatestUpdate: api.latest,
  fetchUpdate: api.detail,
  markUpdateRead: api.read,
}))
vi.mock('vue-router', () => ({
  useRoute: () => route.value,
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
  RouterLink: { name: 'RouterLink', template: '<a><slot /></a>' },
}))
vi.mock('@/stores/session', () => ({ useSessionStore: () => ({ signedIn: false }) }))

import UpdateDetailPage from './UpdateDetailPage.vue'
import UpdateListPage from './UpdateListPage.vue'

const failure = new ApiClientError({ category: 'SYSTEM_FAILURE', reason: 'X', message: '服务暂时不可用' }, 503, 'req-updates')
const stubs = { UiIllustration: true, AppSelect: true, UpdateContent: true }
const release = (slug: string): ReleaseDetail => ({
  release: {
    id: slug, versionLabel: slug, slug, title: `版本 ${slug}`, summary: '摘要', releaseType: 'FEATURE', status: 'PUBLISHED',
    audience: 'ALL', modules: [], showWhatsNew: false, sendNotification: false, currentRevision: 1, versionNo: 1,
    createdAt: '2026-09-01', updatedAt: '2026-09-01', publishedAt: '2026-09-01', readCount: 0,
  },
  sections: [],
})

beforeEach(() => {
  route.value = reactive({ query: {}, params: { version: 'v1' } })
  api.facets.mockResolvedValue({ types: {}, modules: {}, versions: [] })
  api.latest.mockResolvedValue(null)
  api.read.mockResolvedValue(undefined)
})

describe('UpdateListPage', () => {
  it('shows a retryable error, not "还没有发布版本更新", when the list fails', async () => {
    api.list.mockRejectedValueOnce(failure)
    const wrapper = mount(UpdateListPage, { global: { stubs } })
    await flushPromises()
    expect(wrapper.text()).toContain('更新日志读取失败')
    expect(wrapper.text()).toContain('req-updates')
    expect(wrapper.text()).not.toContain('还没有发布版本更新')
  })

  it('shows the empty state only for a successful empty list', async () => {
    api.list.mockResolvedValueOnce({ items: [], total: 0, page: 0, size: 12 })
    const wrapper = mount(UpdateListPage, { global: { stubs } })
    await flushPromises()
    expect(wrapper.text()).toContain('还没有发布版本更新')
  })
})

describe('UpdateDetailPage', () => {
  it('shows a retryable error when the version fails to load', async () => {
    api.detail.mockRejectedValueOnce(failure)
    const wrapper = mount(UpdateDetailPage, { global: { stubs } })
    await flushPromises()
    expect(wrapper.text()).toContain('版本详情读取失败')
    expect(wrapper.text()).toContain('req-updates')
  })

  it('reloads when the previous/next link changes the version in place', async () => {
    api.detail.mockImplementation((version: string) => Promise.resolve(release(version)))
    const wrapper = mount(UpdateDetailPage, { global: { stubs } })
    await flushPromises()
    expect(wrapper.text()).toContain('版本 v1')
    route.value.params.version = 'v2'
    await flushPromises()
    expect(api.detail).toHaveBeenLastCalledWith('v2')
    expect(wrapper.text()).toContain('版本 v2')
  })

  it('shows "没有找到这个版本" for an unknown version', async () => {
    api.detail.mockRejectedValueOnce(new ApiClientError({ category: 'USER_CORRECTABLE', reason: 'NOT_FOUND', message: '不存在' }, 404, 'req-404'))
    const wrapper = mount(UpdateDetailPage, { global: { stubs } })
    await flushPromises()
    expect(wrapper.text()).toContain('没有找到这个版本')
    expect(wrapper.text()).not.toContain('重试')
  })
})
