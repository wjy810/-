/**
 * Saves a blob as a file. The anchor is attached while clicked (a detached one loses its `download`
 * name in some browsers) and the object URL is released only after the browser has taken the file;
 * revoking it synchronously can cancel the download or leave it named "download".
 */
export function saveBlob(blob: Blob, filename: string): void {
  const href = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = href
  anchor.download = filename
  anchor.rel = 'noopener'
  anchor.style.display = 'none'
  document.body.append(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(href), 1000)
}

/** A filename that every OS accepts: no path separators or reserved characters, never empty. */
export function safeFilename(name: string, fallback = 'download'): string {
  const cleaned = name.replace(/[\\/:*?"<>|\u0000-\u001f]+/g, '_').replace(/^[\s.]+|[\s.]+$/g, '').slice(0, 120)
  return cleaned || fallback
}
