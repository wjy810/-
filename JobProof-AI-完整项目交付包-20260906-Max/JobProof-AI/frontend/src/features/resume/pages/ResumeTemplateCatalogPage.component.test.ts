import { flushPromises, mount } from '@vue/test-utils'
import { readFileSync, statSync } from 'node:fs'
import { resolve } from 'node:path'
import { inflateSync } from 'node:zlib'
import { describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  listTemplateCatalog: vi.fn(),
  listTemplateCatalogFacets: vi.fn(),
}))

vi.mock('../services/resumeApi', async () => ({
  ...(await vi.importActual<typeof import('../services/resumeApi')>('../services/resumeApi')),
  ...api,
}))

import ResumeTemplateCatalogPage from './ResumeTemplateCatalogPage.vue'

const illustrationPath = resolve(process.cwd(), 'src/assets/template-smart-editing-construction.png')

function pngHasTransparentPixel(png: Buffer, width: number, height: number): boolean {
  const idatChunks: Buffer[] = []
  let offset = 8
  while (offset < png.length) {
    const length = png.readUInt32BE(offset)
    const type = png.toString('ascii', offset + 4, offset + 8)
    if (type === 'IDAT') idatChunks.push(png.subarray(offset + 8, offset + 8 + length))
    offset += length + 12
  }

  const raw = inflateSync(Buffer.concat(idatChunks))
  const bytesPerPixel = 4
  const stride = width * bytesPerPixel
  let previous = new Uint8Array(stride)
  let rawOffset = 0

  const paeth = (left: number, above: number, upperLeft: number) => {
    const estimate = left + above - upperLeft
    const leftDistance = Math.abs(estimate - left)
    const aboveDistance = Math.abs(estimate - above)
    const upperLeftDistance = Math.abs(estimate - upperLeft)
    if (leftDistance <= aboveDistance && leftDistance <= upperLeftDistance) return left
    return aboveDistance <= upperLeftDistance ? above : upperLeft
  }

  for (let y = 0; y < height; y += 1) {
    const filter = raw[rawOffset]
    rawOffset += 1
    const row = new Uint8Array(stride)
    for (let index = 0; index < stride; index += 1) {
      const encoded = raw[rawOffset + index]
      const left = index >= bytesPerPixel ? row[index - bytesPerPixel] : 0
      const above = previous[index]
      const upperLeft = index >= bytesPerPixel ? previous[index - bytesPerPixel] : 0
      const predictor =
        filter === 1 ? left
          : filter === 2 ? above
            : filter === 3 ? Math.floor((left + above) / 2)
              : filter === 4 ? paeth(left, above, upperLeft)
                : 0
      row[index] = (encoded + predictor) & 0xff
    }
    for (let alphaIndex = 3; alphaIndex < stride; alphaIndex += bytesPerPixel) {
      if (row[alphaIndex] < 255) return true
    }
    previous = row
    rawOffset += stride
  }
  return false
}

describe('ResumeTemplateCatalogPage smart editing state', () => {
  it('shows a construction view without querying the unfinished catalog capability', async () => {
    api.listTemplateCatalogFacets.mockResolvedValue([])
    api.listTemplateCatalog.mockResolvedValue({ items: [], total: 0, page: 0, size: 24 })

    const wrapper = mount(ResumeTemplateCatalogPage, {
      global: {
        stubs: {
          AppChrome: { template: '<main><slot /></main>' },
          RouterLink: { template: '<a><slot /></a>' },
        },
      },
    })
    await flushPromises()

    expect(api.listTemplateCatalog).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('智能编辑')
    expect(wrapper.text()).toContain('建设中')
    expect(wrapper.text()).not.toContain('待开发')

    await wrapper.get('.capability-tabs button:nth-child(2)').trigger('click')

    expect(api.listTemplateCatalog).toHaveBeenCalledTimes(1)
    const constructionPanel = wrapper.get('.construction-state')
    expect(constructionPanel.text()).toContain('智能编辑正在施工')
    expect(constructionPanel.attributes('role')).toBe('tabpanel')
    expect(constructionPanel.attributes('aria-labelledby')).toContain(
      'capability-tab-SMART_EDITABLE',
    )
    expect(wrapper.get('#capability-tab-SMART_EDITABLE').attributes('aria-controls')).toBe(
      'capability-panel-SMART_EDITABLE',
    )
    expect(wrapper.get('.construction-visual img').attributes('src')).toContain(
      'template-smart-editing-construction',
    )
    expect(wrapper.get('.construction-visual img').attributes('alt')).toBe('')
    expect(wrapper.find('.construction-scene').exists()).toBe(false)
    expect(wrapper.find('.construction-sign').exists()).toBe(false)
    expect(wrapper.find('.worker').exists()).toBe(false)
    expect(wrapper.find('.catalog-search').exists()).toBe(false)
    expect(wrapper.find('.filter-band').exists()).toBe(false)
    expect(wrapper.find('.catalog-license').exists()).toBe(false)

    await wrapper.get('.construction-state__actions button').trigger('click')
    await flushPromises()

    expect(api.listTemplateCatalog).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.construction-state').exists()).toBe(false)

    await wrapper.get('.capability-tabs button:nth-child(2)').trigger('click')
    expect(api.listTemplateCatalog).toHaveBeenCalledTimes(2)

    await wrapper.get('.construction-state__actions button:nth-child(2)').trigger('click')
    await flushPromises()

    expect(api.listTemplateCatalog).toHaveBeenCalledTimes(3)
    expect(wrapper.find('.construction-state').exists()).toBe(false)
  })

  it('ships a bounded high-resolution RGBA illustration with transparent pixels', () => {
    const png = readFileSync(illustrationPath)
    expect(png.subarray(0, 8)).toEqual(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]))
    expect(png.readUInt32BE(16)).toBe(1420)
    expect(png.readUInt32BE(20)).toBe(793)
    expect(png[25]).toBe(6)
    expect(statSync(illustrationPath).size).toBeLessThan(1.2 * 1024 * 1024)
    expect(pngHasTransparentPixel(png, 1420, 793)).toBe(true)
  })

  it('does not retain the legacy CSS-built construction scene', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/features/resume/pages/ResumeTemplateCatalogPage.vue'),
      'utf8',
    )
    expect(source).not.toMatch(/\.construction-scene|\.construction-sign|\.worker|\.scene-tool/)
  })
})
