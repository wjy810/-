import { afterEach, describe, expect, it, vi } from 'vitest'
import { imagesReady } from './imagesReady'

afterEach(() => { document.body.innerHTML = ''; vi.unstubAllGlobals(); vi.restoreAllMocks() })

describe('print image readiness', () => {
  it('waits for CSS backgrounds and image decoding, deduplicating shared page art', async () => {
    document.body.innerHTML = '<div class="rr-page"><img src="/portrait.png"><span class="rr-decor"></span><span class="rr-decor"></span></div>'
    vi.spyOn(Element.prototype, 'getClientRects').mockReturnValue([{}] as unknown as DOMRectList)
    vi.spyOn(window, 'getComputedStyle').mockImplementation(element => ({
      backgroundImage: element.classList.contains('rr-decor') ? 'url("http://localhost/texture.webp")' : 'none',
    }) as CSSStyleDeclaration)
    const decoded: string[] = []
    let finish!: () => void
    const pending = new Promise<void>(resolve => { finish = resolve })
    vi.stubGlobal('Image', class {
      src = ''
      decode() { decoded.push(this.src); return pending }
    })
    let settled = false
    const ready = imagesReady(document.body).then(() => { settled = true })
    await Promise.resolve()
    expect(settled).toBe(false)
    expect(decoded).toHaveLength(2)
    expect(decoded.some(url => url.endsWith('/texture.webp'))).toBe(true)
    finish()
    await ready
    expect(settled).toBe(true)
  })

  it('rejects a missing visible asset instead of silently exporting an incomplete PDF', async () => {
    document.body.innerHTML = '<img src="/missing.webp">'
    vi.spyOn(Element.prototype, 'getClientRects').mockReturnValue([{}] as unknown as DOMRectList)
    vi.stubGlobal('Image', class { src = ''; decode() { return Promise.reject(new Error('decode failed')) } })
    await expect(imagesReady(document.body)).rejects.toThrow('decode failed')
  })

  it('does not request switched-off decorations', async () => {
    document.body.innerHTML = '<span class="rr-decor" style="display:none"></span>'
    const decode = vi.fn()
    vi.stubGlobal('Image', class { src = ''; decode = decode })
    await imagesReady(document.body)
    expect(decode).not.toHaveBeenCalled()
  })
})
