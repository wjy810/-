/** Pasted-resume import (docs/phase2/00 B-1): input rules and plain-language results of the parser. */

/** The server parser needs at least this many non-blank characters to find any section. */
export const IMPORT_TEXT_MIN = 80
export const IMPORT_TEXT_MAX = 200_000

export function meaningfulLength(text: string): number {
  return text.replace(/\s+/g, '').length
}

/** Why `text` cannot be submitted, or '' when it can. */
export function importTextIssue(text: string): string {
  if (!text.trim()) return '请先粘贴简历文字。'
  if (text.length > IMPORT_TEXT_MAX) return `内容太长，最多 ${IMPORT_TEXT_MAX} 个字符。`
  const length = meaningfulLength(text)
  if (length < IMPORT_TEXT_MIN) return `内容太短，至少需要 ${IMPORT_TEXT_MIN} 个字（不含空格），现在是 ${length} 个。`
  return ''
}

const FAILURES: Record<string, string> = {
  SCANNED_OR_EMPTY_RESUME: '没有读到足够的简历文字。请粘贴完整的简历内容后再试。',
  RESUME_STRUCTURE_INSUFFICIENT: '没有识别出教育、经历、项目或技能板块。请保留原简历里的板块标题（如“教育经历”“工作经历”“项目经历”“专业技能”）后再试。',
}

const FILE_FAILURES: Record<string, string> = {
  SCANNED_OR_EMPTY_RESUME: '没有从文件里读到文字，它可能是扫描件或图片导出的 PDF。请上传能选中文字的 PDF 或 Word，或改为粘贴文字。',
  RESUME_STRUCTURE_INSUFFICIENT: '文件里没有识别出教育、经历、项目或技能板块。请确认简历保留了“教育经历”“工作经历”等板块标题，或改为粘贴文字后调整。',
  RESUME_IMPORT_TYPE_UNSUPPORTED: '只能导入 PDF 或 Word（.docx）文件。',
}

/** `source`: the import's sourceType; a file gets advice about scans and file types instead of pasting. */
export function importFailureText(errorCode?: string | null, source?: string | null): string {
  const table = source === 'CAREER_FILE' ? FILE_FAILURES : FAILURES
  return (errorCode && (table[errorCode] ?? FAILURES[errorCode])) || '简历解析没有成功，请稍后重试。'
}

export const IMPORT_FILE_MAX_BYTES = 10 * 1024 * 1024
const IMPORT_FILE_TYPES: Record<string, string> = {
  pdf: 'application/pdf',
  docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
}

/** Why `file` cannot be imported, or '' when it can. The server checks the real content again. */
export function importFileIssue(file: { name: string; size: number } | null): string {
  if (!file) return '请先选择简历文件。'
  const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
  if (extension === 'doc') return '暂不支持旧版 .doc 文件，请在 Word 里另存为 .docx 后上传。'
  if (!IMPORT_FILE_TYPES[extension]) return '只能导入 PDF 或 Word（.docx）文件。'
  if (file.size <= 0) return '文件是空的，请重新选择。'
  if (file.size > IMPORT_FILE_MAX_BYTES) return '文件不能超过 10 MB。'
  return ''
}

export function isImportableResumeFile(contentType: string): boolean {
  return Object.values(IMPORT_FILE_TYPES).includes(contentType)
}

/** The library file's safety check (and preview) failed: say why in plain words. */
export function fileTaskFailureText(reason?: string | null): string {
  if (/MALWARE|INFECTED/.test(reason ?? '')) return '文件中检测到不安全的内容，已被删除，没有导入。'
  if (/QUOTA/.test(reason ?? '')) return '求职资料库的空间已满，请先在资料库清理不需要的文件。'
  if (/SCAN_UNAVAILABLE|SCAN_ERROR/.test(reason ?? '')) return '安全检查暂时无法进行，请稍后再试，或先改为粘贴文字。'
  return '文件没有处理成功，请换一个文件，或改为粘贴文字。'
}

export type ImportSummaryItem = { key: string; label: string; count: number; unit: string }

const SECTIONS: Array<{ key: string; label: string; unit: string }> = [
  { key: 'education', label: '教育经历', unit: '段' },
  { key: 'experiences', label: '工作或实习经历', unit: '段' },
  { key: 'projects', label: '项目经历', unit: '个' },
  { key: 'organizations', label: '社团或组织经历', unit: '段' },
  { key: 'skills', label: '技能', unit: '项' },
  { key: 'certificates', label: '证书', unit: '个' },
  { key: 'honors', label: '荣誉', unit: '项' },
  { key: 'languages', label: '语言能力', unit: '项' },
]

function list(value: unknown): Record<string, unknown>[] {
  return Array.isArray(value) ? value.filter((item): item is Record<string, unknown> => Boolean(item) && typeof item === 'object') : []
}

/** What the parser found, section by section; sections it found nothing for are left out. */
export function importDraftSummary(draft: Record<string, unknown> | null | undefined): ImportSummaryItem[] {
  if (!draft) return []
  const items: ImportSummaryItem[] = []
  for (const section of SECTIONS) {
    const records = list(draft[section.key])
    const count = section.key === 'skills'
      ? records.reduce((total, group) => total + (Array.isArray(group.items) ? group.items.length : 1), 0)
      : records.length
    if (count > 0) items.push({ ...section, count })
  }
  if (typeof draft.summary === 'string' && draft.summary.trim()) {
    items.unshift({ key: 'summary', label: '个人简介', count: 1, unit: '段' })
  }
  return items
}

/** Contact fields the parser recognised, e.g. ['姓名', '邮箱']. */
export function importContactFields(draft: Record<string, unknown> | null | undefined): string[] {
  const basics = draft?.basics && typeof draft.basics === 'object' ? draft.basics as Record<string, unknown> : {}
  const labels: Array<[string, string]> = [['name', '姓名'], ['email', '邮箱'], ['phone', '手机号']]
  return labels.filter(([key]) => typeof basics[key] === 'string' && String(basics[key]).trim()).map(([, label]) => label)
}
