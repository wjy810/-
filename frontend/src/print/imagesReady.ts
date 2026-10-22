/** CSS backgrounds are absent from document.images. Decode both before producing a PDF. */
export async function imagesReady(root: ParentNode): Promise<void> {
  const urls = new Set<string>()
  root.querySelectorAll<HTMLImageElement>('img').forEach(image => {
    if (image.getClientRects().length && (image.currentSrc || image.src)) urls.add(image.currentSrc || image.src)
  })
  root.querySelectorAll<HTMLElement>('.rr-decor, .rr-page').forEach(element => {
    if (!element.getClientRects().length) return
    const background = getComputedStyle(element).backgroundImage
    for (const match of background.matchAll(/url\((?:"([^"]*)"|'([^']*)'|([^)]*))\)/g)) {
      const url = (match[1] ?? match[2] ?? match[3] ?? '').trim()
      if (url) urls.add(url)
    }
  })
  await Promise.all([...urls].map(async url => {
    const image = new Image()
    image.src = url
    await image.decode()
  }))
}
