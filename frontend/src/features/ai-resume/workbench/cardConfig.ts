/**
 * Card types and their field descriptors. One table drives both the guided card in the
 * conversation and the module editor (docs/03 §4.7) — add a field here, not in templates.
 */
import type { Component } from 'vue'
import {
  BadgeCheck,
  BriefcaseBusiness,
  Contact,
  FileText,
  FolderKanban,
  GraduationCap,
  Languages,
  Target,
  Trophy,
  UsersRound,
  Wrench,
} from 'lucide-vue-next'
import type { SelectOption } from '@/shared/ui/UiSelect.vue'

export type CardType =
  | 'TARGET_JOB' | 'EDUCATION' | 'EXPERIENCE' | 'PROJECTS' | 'ORGANIZATIONS' | 'SKILLS'
  | 'CERTIFICATES' | 'HONORS' | 'LANGUAGES' | 'CONTACT' | 'SUMMARY'

export type CardMeta = { label: string; description: string; icon: Component; section?: string }

export const CARD_META: Record<CardType, CardMeta> = {
  TARGET_JOB: { label: '目标岗位', description: '第一版必填', icon: Target },
  EDUCATION: { label: '教育经历', description: '教育或经历至少一项', icon: GraduationCap, section: 'education' },
  EXPERIENCE: { label: '工作与实习', description: '教育或经历至少一项', icon: BriefcaseBusiness, section: 'experience' },
  PROJECTS: { label: '项目经历', description: '可以稍后补充', icon: FolderKanban, section: 'projects' },
  ORGANIZATIONS: { label: '社团与活动', description: '学生经历可以补充', icon: UsersRound, section: 'organizations' },
  SKILLS: { label: '专业技能', description: '只填写真实掌握项', icon: Wrench, section: 'skills' },
  CERTIFICATES: { label: '证书与资质', description: '只填写已取得证书', icon: BadgeCheck, section: 'certificates' },
  HONORS: { label: '荣誉奖项', description: '注明可确认的名称与时间', icon: Trophy, section: 'honors' },
  LANGUAGES: { label: '语言能力', description: '注明真实水平或成绩', icon: Languages, section: 'languages' },
  CONTACT: { label: '联系方式', description: '导出前建议补全', icon: Contact },
  SUMMARY: { label: '个人简介', description: '可用 AI 生成三个简介版本', icon: FileText, section: 'summary' },
}

export function cardMeta(type: string): CardMeta {
  return CARD_META[type as CardType] ?? { label: type, description: '', icon: FileText }
}

/** Preview section key → card type (for click-to-edit on the A4 preview). */
export const SECTION_CARD: Record<string, CardType> = Object.fromEntries(
  Object.entries(CARD_META).flatMap(([type, meta]) => (meta.section ? [[meta.section, type as CardType]] : [])),
)

export const SECTION_LABELS: Record<string, string> = {
  summary: '个人简介', education: '教育经历', experience: '工作与实习经历', projects: '项目经历',
  organizations: '社团与活动', skills: '专业技能', certificates: '证书与资质', honors: '荣誉奖项', languages: '语言能力',
}

export const STRUCTURED_CARD_TYPES = new Set<string>(['EDUCATION', 'EXPERIENCE', 'PROJECTS', 'ORGANIZATIONS', 'SKILLS', 'CERTIFICATES', 'HONORS', 'LANGUAGES'])
export const TIMELINE_CARD_TYPES = new Set<string>(['EDUCATION', 'EXPERIENCE', 'PROJECTS', 'ORGANIZATIONS'])
/** Cards whose per-record description can be drafted by AI. */
export const AI_DESCRIPTION_CARD_TYPES = new Set<string>([...TIMELINE_CARD_TYPES, 'LANGUAGES'])
export const CREDENTIAL_CARD_TYPES = new Set<string>(['CERTIFICATES', 'HONORS'])

export const DEGREE_OPTIONS: SelectOption[] = [
  { value: '', label: '请选择' },
  ...['高中', '专科', '本科', '硕士', '博士', '交换生'].map(value => ({ value, label: value })),
]

export const LANGUAGE_OPTIONS: SelectOption[] = [
  { value: '', label: '请选择语言', disabled: true },
  { value: '英语', label: '英语', keywords: 'English en' },
  { value: '日语', label: '日语', keywords: 'Japanese ja' },
  { value: '韩语', label: '韩语', keywords: 'Korean ko' },
  { value: '法语', label: '法语', keywords: 'French fr' },
  { value: '德语', label: '德语', keywords: 'German de' },
  { value: '西班牙语', label: '西班牙语', keywords: 'Spanish es' },
  { value: '葡萄牙语', label: '葡萄牙语', keywords: 'Portuguese pt' },
  { value: '俄语', label: '俄语', keywords: 'Russian ru' },
  { value: '意大利语', label: '意大利语', keywords: 'Italian it' },
  { value: '阿拉伯语', label: '阿拉伯语', keywords: 'Arabic ar' },
  { value: '泰语', label: '泰语', keywords: 'Thai th' },
  { value: '越南语', label: '越南语', keywords: 'Vietnamese vi' },
  { value: '印度尼西亚语', label: '印度尼西亚语', keywords: '印尼语 Indonesian id' },
  { value: '马来语', label: '马来语', keywords: 'Malay ms' },
  { value: '荷兰语', label: '荷兰语', keywords: 'Dutch nl' },
  { value: '波兰语', label: '波兰语', keywords: 'Polish pl' },
  { value: '土耳其语', label: '土耳其语', keywords: 'Turkish tr' },
  { value: '普通话', label: '普通话', keywords: '中文 Mandarin zh' },
  { value: '粤语', label: '粤语', keywords: 'Cantonese yue' },
]

/** Keeps a legacy free-text language selectable instead of silently blanking it. */
export function languageOptionsFor(value: unknown): SelectOption[] {
  const current = String(value ?? '').trim()
  if (!current || LANGUAGE_OPTIONS.some(option => option.value === current)) return LANGUAGE_OPTIONS
  return [...LANGUAGE_OPTIONS, { value: current, label: `${current}（已有）`, keywords: current }]
}

export const LANGUAGE_LEVEL_OPTIONS: SelectOption[] = [
  { value: '', label: '请选择水平', disabled: true },
  ...['入门', '基础', '日常交流', '工作沟通', '熟练', '精通', '母语'].map(value => ({ value, label: value })),
]

export type FieldControl = 'text' | 'select' | 'language' | 'month' | 'current' | 'skills' | 'ai-description' | 'credential-description' | 'textarea'

export type FieldSpec = {
  key: string
  label: string
  control: FieldControl
  wide?: boolean
  placeholder?: string
  options?: SelectOption[]
  /** Receives keyboard focus when the module is opened from the preview or the module list. */
  autofocus?: boolean
}

const TIMELINE: FieldSpec[] = [
  { key: 'startDate', label: '开始时间', control: 'month' },
  { key: 'endDate', label: '结束时间', control: 'month' },
  { key: 'location', label: '城市', control: 'text', placeholder: '例如：杭州' },
  { key: 'current', label: '至今', control: 'current' },
]

export const RECORD_FIELDS: Record<string, FieldSpec[]> = {
  EDUCATION: [
    { key: 'school', label: '学校名称', control: 'text', wide: true, autofocus: true, placeholder: '例如：浙江大学' },
    { key: 'major', label: '专业', control: 'text', placeholder: '例如：计算机科学与技术' },
    { key: 'degree', label: '学历', control: 'select', options: DEGREE_OPTIONS },
    ...TIMELINE,
    { key: 'description', label: '补充描述', control: 'ai-description', wide: true },
  ],
  EXPERIENCE: [
    { key: 'company', label: '公司或组织', control: 'text', wide: true, autofocus: true },
    { key: 'role', label: '岗位', control: 'text', wide: true, placeholder: '例如：后端开发实习生' },
    ...TIMELINE,
    { key: 'description', label: '补充描述', control: 'ai-description', wide: true },
  ],
  PROJECTS: [
    { key: 'name', label: '项目名称', control: 'text', wide: true, autofocus: true },
    { key: 'role', label: '你的角色', control: 'text', wide: true, placeholder: '例如：后端负责人' },
    ...TIMELINE,
    { key: 'description', label: '补充描述', control: 'ai-description', wide: true },
  ],
  ORGANIZATIONS: [
    { key: 'name', label: '组织或活动名称', control: 'text', wide: true, autofocus: true },
    { key: 'role', label: '你的角色', control: 'text', wide: true },
    ...TIMELINE,
    { key: 'description', label: '补充描述', control: 'ai-description', wide: true },
  ],
  SKILLS: [
    { key: 'category', label: '技能类别', control: 'text', autofocus: true, placeholder: '例如：后端开发' },
    { key: 'items', label: '技能条目', control: 'skills', wide: true, placeholder: 'Java、Spring Boot、MySQL' },
    { key: 'description', label: '补充描述', control: 'textarea', wide: true, placeholder: '只填写可以确认的内容' },
  ],
  CERTIFICATES: [
    { key: 'name', label: '证书名称', control: 'text', autofocus: true },
    { key: 'issuer', label: '颁发机构', control: 'text' },
    { key: 'date', label: '取得时间', control: 'month' },
    { key: 'description', label: '补充说明', control: 'credential-description', wide: true },
  ],
  HONORS: [
    { key: 'name', label: '荣誉名称', control: 'text', autofocus: true },
    { key: 'issuer', label: '授予机构', control: 'text' },
    { key: 'date', label: '取得时间', control: 'month' },
    { key: 'description', label: '补充说明', control: 'credential-description', wide: true },
  ],
  LANGUAGES: [
    { key: 'language', label: '语言', control: 'language', autofocus: true },
    { key: 'level', label: '水平', control: 'select', options: LANGUAGE_LEVEL_OPTIONS },
    { key: 'score', label: '成绩或证明', control: 'text', wide: true, placeholder: '例如：CET-6 520' },
    { key: 'description', label: '使用场景', control: 'ai-description', wide: true },
  ],
}

/** Field labels shown as "AI used: 学校、专业…" under a generated description. */
export const DESCRIPTION_FIELD_LABELS: Record<string, string> = {
  school: '学校', major: '专业', degree: '学历', company: '公司或组织', role: '角色', name: '名称',
  department: '部门', startDate: '开始时间', endDate: '结束时间', current: '至今', location: '城市',
  description: '已有描述', targetJob: '目标岗位', language: '语言', level: '水平', score: '成绩或证明',
}

export function descriptionPlaceholder(cardType: string): string {
  if (cardType === 'LANGUAGES') return '可选：填写真实使用场景；留空时 AI 会生成三条待确认参考'
  return cardType === 'EDUCATION'
    ? '可选：填写真实课程、实践或成果；留空时 AI 将生成待确认参考'
    : '可选：填写真实职责或成果；留空时 AI 将根据基础信息生成待确认参考'
}
