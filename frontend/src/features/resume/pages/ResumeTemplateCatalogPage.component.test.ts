import { flushPromises, mount } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  listTemplateCatalog: vi.fn(),
  listTemplateCatalogFacets: vi.fn(),
  listResumeTemplates: vi.fn(),
}))
const router = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn(), query: {} as Record<string, string> }))

vi.mock('../services/resumeApi', async () => ({
  ...(await vi.importActual<typeof import('../services/resumeApi')>('../services/resumeApi')),
  ...api,
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: router.query }),
  useRouter: () => ({ push: router.push, replace: router.replace }),
  RouterLink: { name: 'RouterLink', template: '<a><slot /></a>' },
}))

import ResumeTemplateCatalogPage from './ResumeTemplateCatalogPage.vue'

const template = (id: string, pages = '1') => ({
  id, displayName: `模板 ${id}`, familyName: id, languageCode: 'zh-CN', recommendedPages: pages, atsCandidateLevel: 'HIGH',
  photoPolicy: 'DISABLED', variants: ['MONO', 'BLUE'], tags: [], status: 'DEMO', rendererProtocol: 'resume-layout-v3', layoutDefinitionJson: '{}',
})

function mountPage() {
  return mount(ResumeTemplateCatalogPage, {
    global: { stubs: { ResumeTemplatePreview: true, UiTooltip: { template: '<slot />' } } },
  })
}

describe('ResumeTemplateCatalogPage', () => {
  beforeEach(() => {
    Object.values(api).forEach(mock => mock.mockReset())
    router.push.mockReset()
    router.replace.mockReset()
    router.query = {}
    api.listTemplateCatalogFacets.mockResolvedValue([])
    api.listTemplateCatalog.mockResolvedValue({ items: [], total: 0, page: 0, size: 24 })
    api.listResumeTemplates.mockResolvedValue({ items: [template('a'), template('b', '2')], total: 2, page: 0, size: 50 })
  })

  it('opens on the smart templates, rendered live, without querying the Word catalog', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(api.listResumeTemplates).toHaveBeenCalledTimes(1)
    expect(api.listTemplateCatalog).not.toHaveBeenCalled()
    const panel = wrapper.get('#capability-panel-SMART_EDITABLE')
    expect(panel.attributes('role')).toBe('tabpanel')
    expect(wrapper.findAll('.smart-card')).toHaveLength(2)
    expect(wrapper.text()).not.toContain('建设中')
    expect(wrapper.find('.filter-band').exists()).toBe(false)

    await wrapper.findAll('.smart-card button').at(1)!.trigger('click')
    expect(router.push).toHaveBeenCalledWith({ path: '/ai-resume/new', query: { template: 'b' } })
  })

  it('switches to the Word catalog, syncing the tab to the URL', async () => {
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.get('#capability-tab-DOCX_DOWNLOAD').trigger('click')
    await flushPromises()

    expect(router.replace).toHaveBeenCalledWith({ query: { tab: 'word' } })
    expect(api.listTemplateCatalog).toHaveBeenCalledTimes(1)
    expect(api.listTemplateCatalog.mock.calls[0]![0]).toMatchObject({ capability: 'DOCX_DOWNLOAD', page: 0 })
    expect(wrapper.find('.smart-gallery').exists()).toBe(false)
    expect(wrapper.find('.filter-band').exists()).toBe(true)
  })

  it('honours ?tab=all on entry', async () => {
    router.query = { tab: 'all' }
    mountPage()
    await flushPromises()
    expect(api.listTemplateCatalog).toHaveBeenCalledTimes(1)
    expect(api.listTemplateCatalog.mock.calls[0]![0]).toMatchObject({ capability: '' })
  })

  it('no longer ships the construction placeholder', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/features/resume/pages/ResumeTemplateCatalogPage.vue'), 'utf8')
    expect(source).not.toMatch(/construction|建设中/)
  })
})
