import type { AiSkillGroupSuggestion } from '../types'

export type SkillDraftItem = Record<string, unknown>

export function mergeSkillGroups(
  current: SkillDraftItem[],
  incoming: AiSkillGroupSuggestion[],
): SkillDraftItem[] {
  const result: SkillDraftItem[] = current.filter(hasContent).map((item) => ({
    ...item,
    items: skillNames(item.items),
  }))
  for (const group of incoming) {
    const category = canonicalSkillCategory(group.category)
    const index = result.findIndex((item) => canonicalSkillCategory(text(item.category)) === category)
    if (index < 0) {
      result.push({ category, items: unique(group.items), description: group.description.trim() })
      continue
    }
    const existing = result[index]
    result[index] = {
      ...existing,
      category,
      items: unique([...skillNames(existing.items), ...group.items]),
      description: mergeDescriptions(text(existing.description), group.description),
    }
  }
  return result.length ? result : [{ category: '', items: [], description: '' }]
}

export function canonicalSkillCategory(value: string): string {
  const key = text(value).toLowerCase().replace(/[\s\p{P}，、；：·（）]/gu, '')
  if (/前端|webui|frontend/.test(key)) return '前端开发'
  if (/数据库|存储|缓存|sql|database/.test(key)) return '数据库与存储'
  if (/后端|服务端|持久层|接口|backend/.test(key)) return '后端开发'
  if (/编程语言|开发语言|programminglanguage/.test(key) || key === '语言') return '编程语言'
  if (/数据|算法|智能|机器学习|ai|algorithm/.test(key)) return '数据与智能'
  if (/测试|质量|test|qa/.test(key)) return '测试与质量'
  if (/构建|版本|依赖|协作|工程|开发工具|tool/.test(key)) return '工程工具'
  if (/云|运维|容器|操作系统|中间件|devops|cloud/.test(key)) return '云与运维'
  if (/产品|运营|业务/.test(key)) return '产品与业务'
  if (/设计|视觉|交互/.test(key)) return '设计工具'
  if (/通用|沟通|管理|协同/.test(key)) return '通用能力'
  return '其他技能'
}

function mergeDescriptions(existing: string, incoming: string): string {
  const lines = uniqueDescriptionLines([...descriptionLines(existing), ...descriptionLines(incoming)])
  return lines.map((line) => `• ${line}`).join('\n')
}

function descriptionLines(value: string): string[] {
  return text(value).split(/\r?\n/).map((line) => line.trim().replace(/^[•·*\-]\s*/, '').trim()).filter(Boolean)
}

function uniqueDescriptionLines(values: string[]): string[] {
  const seen = new Set<string>()
  return values.filter((value) => {
    const key = value.toLowerCase().replace(/\s+/g, '')
    if (!key || seen.has(key)) return false
    seen.add(key)
    return true
  })
}

function hasContent(item: SkillDraftItem): boolean {
  return text(item.category).length > 0 || text(item.description).length > 0 || skillNames(item.items).length > 0
}

function skillNames(value: unknown): string[] {
  if (!Array.isArray(value)) return []
  return unique(value.map(text))
}

function unique(values: string[]): string[] {
  const seen = new Set<string>()
  return values.map(text).filter((value) => {
    if (!value) return false
    const key = value.toLowerCase()
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}

function text(value: unknown): string {
  return String(value ?? '').trim()
}
