export type PdfOverflowItemLike = {
  page: number
  slotKey: string
  excessUnits: number
  suggestedAction?: string
}

export type PdfLayoutLike = {
  status?: string | null
  overflow?: {
    valid?: boolean
    items?: PdfOverflowItemLike[]
  } | null
} | null | undefined

const sectionLabels: Record<string, string> = {
  summary: '个人简介',
  education: '教育经历',
  experience: '工作与实习经历',
  projects: '项目经历',
  organizations: '社团与活动',
  skills: '专业技能',
  certificates: '证书与资质',
  honors: '荣誉奖项',
  languages: '语言能力',
  DOCUMENT: '整份简历',
}

export function pdfOverflowItems(layout: PdfLayoutLike): PdfOverflowItemLike[] {
  if (!layout || (layout.status !== 'OVERFLOW' && layout.overflow?.valid !== false)) return []
  return Array.isArray(layout.overflow?.items) ? layout.overflow.items : []
}

export function isPdfOverflowBlocked(layout: PdfLayoutLike): boolean {
  return Boolean(layout && (layout.status === 'OVERFLOW' || layout.overflow?.valid === false))
}

export function formatPdfOverflow(item: PdfOverflowItemLike | null | undefined): string {
  if (!item) return '当前模板的 PDF 容量不足'
  const label = sectionLabels[item.slotKey] ?? (item.slotKey || '内容区域')
  return `第 ${Math.max(1, item.page)} 页的${label}超出 ${Math.max(0, item.excessUnits)} 个容量单位`
}
