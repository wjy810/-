export type PdfExportModeLike = 'STANDARD' | 'ANONYMOUS'

export function needsPdfContactWarning(
  mode: PdfExportModeLike,
  basics: Record<string, unknown>,
): boolean {
  if (mode === 'ANONYMOUS') return false
  return !String(basics.email ?? '').trim() && !String(basics.phone ?? '').trim()
}
