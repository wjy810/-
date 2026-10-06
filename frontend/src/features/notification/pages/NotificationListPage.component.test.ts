import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '@/shared/api/types'

const api = vi.hoisted(() => ({ list: vi.fn(), unread: vi.fn(), task: vi.fn() }))
vi.mock('../services/notificationApi', async (importOriginal) => ({
  ...await importOriginal<typeof import('../services/notificationApi')>(),
  listNotifications: api.list,
  fetchUnreadCount: api.unread,
  fetchTaskDetail: api.task,
}))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  RouterLink: { name: 'RouterLink', template: '<a><slot /></a>' },
}))

import NotificationListPage from './NotificationListPage.vue'

const failure = new ApiClientError({ category: 'SYSTEM_FAILURE', reason: 'X', message: '服务暂时不可用' }, 503, 'req-notice')

function mountPage() {
  return mount(NotificationListPage, { global: { stubs: { UiIllustration: true, AppDrawer: true, JobProofIcon: true } } })
}

describe('NotificationListPage', () => {
  beforeEach(() => {
    api.unread.mockResolvedValue(0)
    api.list.mockResolvedValue({ items: [], total: 0, page: 0, size: 20 })
  })

  it('shows a retryable error with the request id instead of "no notifications" when loading fails', async () => {
    api.list.mockRejectedValueOnce(failure)
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.text()).toContain('通知读取失败')
    expect(wrapper.text()).toContain('req-notice')
    expect(wrapper.text()).not.toContain('还没有站内通知')
    expect(wrapper.text()).not.toContain('共 0 条')
    await wrapper.findAll('button').find(button => button.text().includes('重试'))!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('还没有站内通知')
  })

  it('labels task types in plain language', async () => {
    api.list.mockResolvedValue({ items: [{ id: 'n1', type: 'TASK_COMPLETED', status: 'DELIVERED', title: '导出完成', eventId: 'task-1', createdAt: new Date().toISOString() }], total: 1, page: 0, size: 20 })
    api.task.mockResolvedValue({ id: 'task-1', taskType: 'ACCOUNT_EXPORT', status: 'SUCCEEDED', inputVersion: 'sha-abc' })
    const wrapper = mount(NotificationListPage, { global: { stubs: { UiIllustration: true, JobProofIcon: true, AppDrawer: { template: '<div class="drawer-stub"><slot /></div>' } } } })
    await flushPromises()
    await wrapper.get('.n-row').trigger('click')
    await flushPromises()
    const drawer = wrapper.get('.drawer-stub').text()
    expect(drawer).toContain('导出个人数据')
    expect(drawer).not.toContain('ACCOUNT_EXPORT')
    expect(drawer).not.toContain('sha-abc')
  })
})
