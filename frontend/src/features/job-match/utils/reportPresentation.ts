import type { EvidenceSource } from '../types'

export const jobMatchDisplayLabels: Record<string, string> = {
  CAPABILITY_GAP: '能力缺口',
  VERIFICATION_GAP: '证据缺口',
  EVIDENCE_GAP: '证据缺口',
  EXPRESSION_GAP: '表达缺口',
  OBJECTIVE_CONSTRAINT: '客观限制',
  BONUS_ITEM: '加分项',
  STRENGTH: '匹配优势',
  GAP: '待处理项',
  RESUME_SUPPORTED: '简历已体现',
  LIBRARY_SUPPORTED: '资料证据支持',
  USER_CONFIRMED: '用户已确认',
  INSUFFICIENT: '证据不足',
  NOT_FOUND: '暂未发现证据',
  MET: '满足',
  PASSED: '通过',
  NOT_MET: '未满足',
  FAILED: '未通过',
  PARTIAL: '部分满足',
  UNKNOWN: '待核实',
  CRITICAL: '最高优先',
  HIGH: '高优先',
  MEDIUM: '中优先',
  LOW: '低优先',
  MUST: '必须',
  IMPORTANT: '重要',
  BONUS: '加分',
  PENDING: '待完成',
  IN_PROGRESS: '进行中',
  DONE: '已完成',
  BEFORE_APPLY: '投递前',
  BEFORE_INTERVIEW: '面试前',
  IMMEDIATE: '立即处理',
  SHORT_TERM: '短期',
  MID_TERM: '中期',
  LONG_TERM: '长期',
}

/** UPPER_SNAKE values are internal codes; one without a label is not shown to users. */
const INTERNAL_CODE = /^[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+$/

export function displayJobMatchText(value: unknown): string {
  if (typeof value !== 'string') return ''
  const trimmed = value.trim()
  const exact = jobMatchDisplayLabels[trimmed]
  if (exact) return exact
  if (INTERNAL_CODE.test(trimmed)) return ''
  return trimmed
    .replaceAll('hardGatePassed', '硬性资格结果')
    .replaceAll('overallScore', '综合匹配度')
    .replaceAll('ruleResult 判定', '规则引擎判定')
    .replaceAll('ruleResult 仍为', '规则结果仍为')
    .replaceAll('ruleResult=', '规则结果为')
    .replaceAll('ruleResult', '规则结果')
    .replaceAll('NOT_FOUND', '暂未发现证据')
    .replaceAll('evidence', '证据')
    .replace(/硬性资格结果\s*为\s*false/g, '硬性资格未通过')
    .replace(/综合匹配度\s*为\s*/g, '综合匹配度为')
    .replace(/已授权\s+证据/g, '已授权证据')
    .replace(/授权\s+证据/g, '授权证据')
    .replace(/为\s+暂未发现证据/g, '为暂未发现证据')
    .replace(/(规则引擎判定|规则结果仍)\s*为\s*暂未发现证据/g, '$1为暂未发现证据')
    .replace(/(当前)?证据\s+为空/g, '$1证据为空')
    .replace(/规则结果\s+(?=[一-鿿])/g, '规则结果')
    .replace(/证据\s+(?=[一-鿿])/g, '证据')
    .replace(/([一-鿿])\s+(证据|规则结果)/g, '$1$2')
}

export function jobMatchStatusTone(value: unknown): 'green' | 'orange' | 'gray' {
  if (typeof value !== 'string') return 'gray'
  if (['RESUME_SUPPORTED', 'LIBRARY_SUPPORTED', 'USER_CONFIRMED', 'MET', 'PASSED'].includes(value)) return 'green'
  if (['INSUFFICIENT', 'NOT_FOUND', 'NOT_MET', 'FAILED', 'PARTIAL'].includes(value)) return 'orange'
  return 'gray'
}

const RECOMMENDATIONS: Record<string, string> = {
  RECOMMENDED: '推荐投递',
  CONDITIONAL: '准备后投递',
  CAUTION: '谨慎评估',
  NOT_RECOMMENDED: '暂不建议投递',
}

/** The report's application advice, or '' when it gave none (never a default verdict). */
export function recommendationLabel(code: unknown): string {
  return typeof code === 'string' ? RECOMMENDATIONS[code] ?? '' : ''
}

const SOURCE_TYPES: Record<string, string> = {
  CAREER_RECORD: '资料库记录',
  CAREER_FILE: '资料库文件',
}

/**
 * Human-readable provenance for an evidence id: the source kind and its title. An id the report
 * no longer lists (for example after authorization was revoked) gets a generic label, never a
 * slice of the id.
 */
export function evidenceSourceLabel(id: unknown, sources: readonly EvidenceSource[] | null | undefined): string {
  const source = sources?.find(item => item.id === String(id))
  if (!source) return '已授权资料（详情不可用）'
  const kind = SOURCE_TYPES[source.sourceType] ?? '资料库'
  return source.title?.trim() ? `${kind}：${source.title.trim()}` : kind
}
