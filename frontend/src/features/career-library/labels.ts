/** Plain-language names for career-library record types and file categories, shared by every view. */
export type LabelOption = { value: string; label: string }

export const RECORD_TYPES: LabelOption[] = [
  { value: 'EDUCATION', label: '教育经历' }, { value: 'EXPERIENCE', label: '工作经历' },
  { value: 'PROJECT', label: '项目经历' }, { value: 'ORGANIZATION', label: '组织与社团' },
  { value: 'SKILL', label: '专业技能' }, { value: 'CERTIFICATE', label: '证书' },
  { value: 'HONOR', label: '荣誉' }, { value: 'LANGUAGE', label: '语言能力' },
  { value: 'ACHIEVEMENT', label: '成果记录' },
]

export const FILE_CATEGORIES: LabelOption[] = [
  { value: 'RESUME', label: '简历' }, { value: 'PORTFOLIO', label: '作品集' },
  { value: 'WORK_SAMPLE', label: '工作样本' }, { value: 'CERTIFICATE', label: '证书' },
  { value: 'TRANSCRIPT', label: '成绩单' }, { value: 'PROOF', label: '证明材料' },
  { value: 'OTHER', label: '其他' },
]

export function recordTypeLabel(type: string): string {
  return RECORD_TYPES.find(item => item.value === type)?.label || type
}

export function fileCategoryLabel(category: string): string {
  return FILE_CATEGORIES.find(item => item.value === category)?.label || category
}

/** "820 KB" / "1.2 MB". */
export function fileSize(bytes: number): string {
  return bytes < 1024 * 1024 ? `${bytes > 0 ? Math.max(1, Math.round(bytes / 1024)) : 0} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
