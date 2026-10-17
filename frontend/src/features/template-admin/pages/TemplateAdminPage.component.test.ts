import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import type { CatalogAdminItem } from '../types'

const api = vi.hoisted(() => ({
  catalog: vi.fn(),
  publish: vi.fn(),
}))
vi.mock('../services/templateAdminApi', async (importOriginal) => ({
  ...await importOriginal<typeof import('../services/templateAdminApi')>(),
  listTemplateFamilies: vi.fn().mockResolvedValue([]),
  listTemplateAssets: vi.fn().mockResolvedValue({ items: [], total: 0, page: 0, size: 100 }),
  listTemplateEvidence: vi.fn().mockResolvedValue({ items: [], total: 0, page: 0, size: 100 }),
  getMalwareScannerStatus: vi.fn().mockResolvedValue({ enabled: true, ready: true }),
  listImportBatches: vi.fn().mockResolvedValue({ items: [], total: 0, page: 0, size: 100 }),
  listCatalogEntries: api.catalog,
  publishCatalogEntry: api.publish,
}))

import TemplateAdminPage from './TemplateAdminPage.vue'

function entry(index: number, status = 'DRAFT'): CatalogAdminItem {
  return {
    item: {
      id: `c${index}`, entryType: 'DOCX_ASSET', referenceId: `r${index}`, title: `模板 ${index}`, capability: 'DOWNLOAD',
      assetKind: 'RESUME', languageCode: 'zh-CN', photoPolicy: 'UNSPECIFIED', sourceName: 'HICV', sourceUri: '', attribution: '',
      downloadCount: 0, facets: [],
    },
    publicationStatus: status,
    updatedAt: '2026-10-01T00:00:00Z',
    version: 1,
  }
}

async function openCatalog() {
  const wrapper = mount(TemplateAdminPage, { global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } } })
  await flushPromises()
  await wrapper.findAll('button').find(button => button.text() === '公共目录')!.trigger('click')
  await flushPromises()
  return wrapper
}

describe('TemplateAdminPage public catalog', () => {
  it('pages through the whole catalog instead of stopping at the first 100 entries', async () => {
    api.catalog.mockImplementation(async (page = 0) => ({
      items: Array.from({ length: page === 0 ? 100 : 30 }, (_, i) => entry(page * 100 + i)),
      total: 130, page, size: 100,
    }))
    const wrapper = await openCatalog()
    expect(wrapper.text()).toContain('已显示 100 / 共 130 条')

    await wrapper.findAll('button').find(button => button.text().includes('加载更多'))!.trigger('click')
    await flushPromises()
    expect(api.catalog).toHaveBeenLastCalledWith(1)
    expect(wrapper.text()).toContain('已显示 130 / 共 130 条')
    expect(wrapper.findAll('button').some(button => button.text().includes('加载更多'))).toBe(false)
  })

  it('updates a published row in place, keeping the loaded pages', async () => {
    api.catalog.mockImplementation(async (page = 0) => ({
      items: Array.from({ length: page === 0 ? 100 : 30 }, (_, i) => entry(page * 100 + i)),
      total: 130, page, size: 100,
    }))
    api.publish.mockImplementation(async (id: string) => ({ ...entry(Number(id.slice(1)), 'PUBLISHED'), version: 2 }))
    const wrapper = await openCatalog()
    await wrapper.findAll('button').find(button => button.text().includes('加载更多'))!.trigger('click')
    await flushPromises()

    await wrapper.findAll('button[title="发布"]')[120]!.trigger('click')
    await flushPromises()
    expect(api.publish).toHaveBeenCalledWith('c120', 1)
    expect(wrapper.text()).toContain('已显示 130 / 共 130 条')
    expect(api.catalog).toHaveBeenCalledTimes(2)
  })
})
