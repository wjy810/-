export type CareerSkillStatus = 'PRACTICED' | 'LEARNING'

export type CareerSkillDraft = {
  name: string
  status: CareerSkillStatus
  custom?: boolean
}

export type CareerSkillOption = {
  name: string
  category: string
}

export const careerSkillCategories = ['全部', '技术研发', '产品运营', '设计创意', '市场销售', '教育研究', '职能通用'] as const

export const careerSkillCatalog: CareerSkillOption[] = [
  { name: 'Java', category: '技术研发' },
  { name: 'Spring Boot', category: '技术研发' },
  { name: 'MySQL', category: '技术研发' },
  { name: 'Python', category: '技术研发' },
  { name: 'JavaScript / TypeScript', category: '技术研发' },
  { name: 'Vue', category: '技术研发' },
  { name: 'React', category: '技术研发' },
  { name: 'Node.js', category: '技术研发' },
  { name: 'Go', category: '技术研发' },
  { name: 'C / C++', category: '技术研发' },
  { name: 'Linux', category: '技术研发' },
  { name: 'Docker', category: '技术研发' },
  { name: 'Kubernetes', category: '技术研发' },
  { name: 'Git', category: '技术研发' },
  { name: 'REST API', category: '技术研发' },
  { name: '数据结构与算法', category: '技术研发' },
  { name: '自动化测试', category: '技术研发' },
  { name: '产品需求分析', category: '产品运营' },
  { name: '用户研究', category: '产品运营' },
  { name: '数据分析', category: '产品运营' },
  { name: '活动运营', category: '产品运营' },
  { name: '内容运营', category: '产品运营' },
  { name: 'Figma', category: '设计创意' },
  { name: 'UI 设计', category: '设计创意' },
  { name: '交互设计', category: '设计创意' },
  { name: '用户体验设计', category: '设计创意' },
  { name: '客户开发', category: '市场销售' },
  { name: '商务谈判', category: '市场销售' },
  { name: '市场分析', category: '市场销售' },
  { name: '新媒体运营', category: '市场销售' },
  { name: '教学设计', category: '教育研究' },
  { name: '课程开发', category: '教育研究' },
  { name: '课题研究', category: '教育研究' },
  { name: 'Excel', category: '职能通用' },
  { name: 'PowerPoint', category: '职能通用' },
  { name: '项目管理', category: '职能通用' },
  { name: '沟通协作', category: '职能通用' },
  { name: '英语', category: '职能通用' },
]

export function normalizeCareerSkillName(value: string): string {
  return value.trim().replace(/\s+/g, ' ').toLocaleLowerCase('zh-CN')
}

export function upsertCareerSkill(
  skills: CareerSkillDraft[],
  skill: CareerSkillDraft,
): CareerSkillDraft[] {
  const normalized = normalizeCareerSkillName(skill.name)
  if (!normalized) return [...skills]
  const index = skills.findIndex(item => normalizeCareerSkillName(item.name) === normalized)
  if (index < 0) return [...skills, { ...skill, name: skill.name.trim() }]
  return skills.map((item, itemIndex) => itemIndex === index
    ? { ...item, ...skill, name: item.name, custom: item.custom || skill.custom }
    : item)
}

export function removeCareerSkill(skills: CareerSkillDraft[], name: string): CareerSkillDraft[] {
  const normalized = normalizeCareerSkillName(name)
  return skills.filter(item => normalizeCareerSkillName(item.name) !== normalized)
}

export function filterCareerSkillOptions(
  options: CareerSkillOption[],
  query: string,
  category: string,
): CareerSkillOption[] {
  const normalizedQuery = normalizeCareerSkillName(query)
  return options.filter(option =>
    (category === '全部' || option.category === category)
    && (!normalizedQuery || normalizeCareerSkillName(`${option.name} ${option.category}`).includes(normalizedQuery)),
  )
}
