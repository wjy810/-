/**
 * Copies text, falling back to a hidden textarea where the Clipboard API is missing or refused
 * (an http origin, an embedded browser). Resolves false when neither worked.
 */
export async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    const field = document.createElement('textarea')
    field.value = text
    field.setAttribute('readonly', '')
    field.style.position = 'fixed'
    field.style.opacity = '0'
    document.body.append(field)
    field.select()
    let copied = false
    try {
      copied = document.execCommand('copy')
    } catch {
      copied = false
    }
    field.remove()
    return copied
  }
}
