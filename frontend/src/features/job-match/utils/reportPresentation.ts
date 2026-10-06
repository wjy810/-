export const jobMatchDisplayLabels: Record<string, string> = {
  CAPABILITY_GAP: '能力缺口',
  VERIFICATION_GAP: '证据缺口',
  EVIDENCE_GAP: '证据缺口',
  EXPRESSION_GAP: '表达缺口',
  OBJECTIVE_CONSTRAINT: '客观限制',
  BONUS_ITEM: '加分项',
  STRENGTH: '匹配优势',
  RESUME_SUPPORTED: '简历已体现',
  LIBRARY_SUPPORTED: '资料证据支持',
  USER_CONFIRMED: '用户已确认',
  INSUFFICIENT: '证据不足',
  NOT_FOUND: '暂未发现证据',
  CRITICAL: '最高优先',
  HIGH: '高优先',
  MEDIUM: '中优先',
  LOW: '低优先',
  PENDING: '待完成',
  IN_PROGRESS: '进行中',
  DONE: '已完成',
}

export function displayJobMatchText(value: unknown): string {
  if (typeof value !== 'string') return ''
  const trimmed = value.trim()
  const exact = jobMatchDisplayLabels[trimmed]
  if (exact) return exact
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
    .replace(/规则结果\s+(?=[\u4e00-\u9fff])/g, '规则结果')
    .replace(/证据\s+(?=[\u4e00-\u9fff])/g, '证据')
    .replace(/([\u4e00-\u9fff])\s+(证据|规则结果)/g, '$1$2')
}

export function jobMatchStatusTone(value: unknown): 'green' | 'orange' | 'gray' {
  if (typeof value !== 'string') return 'gray'
  if (['RESUME_SUPPORTED', 'LIBRARY_SUPPORTED', 'USER_CONFIRMED'].includes(value)) return 'green'
  if (['INSUFFICIENT', 'NOT_FOUND'].includes(value)) return 'orange'
  return 'gray'
}
