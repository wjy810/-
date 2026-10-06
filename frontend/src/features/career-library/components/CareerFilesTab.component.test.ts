import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '@/shared/api/types'
import UiDropdownMenu, { type MenuItem } from '@/shared/ui/UiDropdownMenu.vue'
import CareerFilesTab from './CareerFilesTab.vue'
import type { CareerFile, CareerOverview } from '../types'

const api = vi.hoisted(() => ({ files: vi.fn(), folders: vi.fn(), storage: vi.fn() }))
vi.mock('../services/careerLibraryApi', async (importOriginal) => ({
  ...await importOriginal<typeof import('../services/careerLibraryApi')>(),
  fetchCareerFiles: api.files,
  fetchCareerFolders: api.folders,
  fetchCareerStorage: api.storage,
}))

const file: CareerFile = {
  id: 'file-1', privateFileId: 'private-1', category: 'RESUME', displayName: '我的简历', originalFilename: 'cv.pdf',
  contentType: 'application/pdf', sizeBytes: 2048, sha256: 'x', scanStatus: 'CLEAN', previewStatus: 'READY',
  previewPageCount: 1, status: 'ACTIVE', processingStatus: 'READY', processingAttempts: 1, version: 1,
  createdAt: '2026-09-06T00:00:00Z', updatedAt: '2026-09-06T00:00:00Z',
}
const overview = {} as CareerOverview
const failure = new ApiClientError({ category: 'SYSTEM_FAILURE', reason: 'X', message: '服务暂时不可用' }, 503, 'req-files')

beforeEach(() => {
  api.files.mockResolvedValue({ items: [file], total: 1, page: 0, size: 48 })
  api.folders.mockResolvedValue([])
  api.storage.mockResolvedValue({ usedBytes: 2048, quotaBytes: 20 * 1024 * 1024, availableBytes: 20 * 1024 * 1024 - 2048, categoryCounts: { RESUME: 1 } })
})

async function mountTab() {
  const wrapper = mount(CareerFilesTab, {
    props: { overview, q: '', type: '', status: 'ALL', sort: 'RECENT', layout: 'grid', folderId: '' },
    global: { stubs: { UiIllustration: true, RouterLink: { template: '<a><slot /></a>' } } },
  })
  await flushPromises()
  return wrapper
}

describe('career files', () => {
  it('shows a retryable error, not "还没有符合条件的文件", when the list fails', async () => {
    api.files.mockRejectedValueOnce(failure)
    const wrapper = await mountTab()
    expect(wrapper.text()).toContain('文件资料读取失败')
    expect(wrapper.text()).not.toContain('还没有符合条件的文件')
  })

  it('does not invent a quota or zero counts when the storage summary fails', async () => {
    api.storage.mockRejectedValueOnce(failure)
    const wrapper = await mountTab()
    expect(wrapper.get('.storage-card').text()).toContain('存储空间读取失败')
    expect(wrapper.get('.storage-card').text()).not.toContain('MB')
    expect(wrapper.get('.category-overview').find('strong').exists()).toBe(false)
  })

  it('offers archive and permanent deletion from the more-actions menu', async () => {
    const wrapper = await mountTab()
    const items = wrapper.getComponent(UiDropdownMenu).props('items') as MenuItem[]
    const labels = items.flatMap(item => ('label' in item ? [item.label] : []))
    expect(labels).toEqual(['预览', '下载', '重命名或移动', '归档', '永久删除…'])
    const remove = items.find(item => 'label' in item && item.label === '永久删除…') as Extract<MenuItem, { onSelect: () => void }>
    remove.onSelect()
    expect(wrapper.emitted('remove')).toEqual([[{ type: 'CAREER_FILE', id: file.id }]])
  })
})
