export const TYPE_LABELS: Record<string, string> = {
  FEATURE: '新功能', IMPROVEMENT: '体验优化', FIX: '问题修复', SECURITY: '安全更新',
  DEPRECATED: '下线说明', PLANNED: '规划中',
}

export const STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿', SCHEDULED: '定时发布', PUBLISHED: '已发布', ARCHIVED: '已归档',
}

export const MODULE_LABELS: Record<string, string> = {
  AI_RESUME: 'AI 简历', CAREER_LIBRARY: '求职资料库', JOB_MATCHING: '岗位匹配',
  MOCK_INTERVIEW: '模拟面试', CAREER_PLANNING: '职业规划',
  TEMPLATE_CENTER: '模板中心', ACCOUNT: '账号体系', NOTIFICATIONS: '通知中心', PLATFORM: '平台体验',
}

export const SECTION_LABELS: Record<string, string> = {
  HIGHLIGHTS: '本次更新亮点', FEATURES: '新增功能', IMPROVEMENTS: '体验优化',
  FIXES: '问题修复', IMPORTANT: '重要说明', COMPATIBILITY: '兼容与升级',
}

export function typeLabel(value: string): string { return TYPE_LABELS[value] ?? value }
export function statusLabel(value: string): string { return STATUS_LABELS[value] ?? value }
export function moduleLabel(value: string): string { return MODULE_LABELS[value] ?? value }
export function sectionLabel(value: string): string { return SECTION_LABELS[value] ?? value }
