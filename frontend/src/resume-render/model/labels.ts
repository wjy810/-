import type { Locale, SectionKey } from './types'

export const SECTION_TITLES: Record<Locale, Record<SectionKey, string>> = {
  'zh-CN': {
    summary: '个人简介', experience: '工作经历', projects: '项目经历', education: '教育经历', organizations: '校园与社团',
    skills: '专业技能', certificates: '证书资质', honors: '荣誉奖项', languages: '语言能力',
  },
  en: {
    summary: 'Summary', experience: 'Experience', projects: 'Projects', education: 'Education', organizations: 'Leadership & Activities',
    skills: 'Skills', certificates: 'Certifications', honors: 'Honors & Awards', languages: 'Languages',
  },
}

export const PAGE_LABEL: Record<Locale, (page: number, total: number) => string> = {
  'zh-CN': (page, total) => `第 ${page} / ${total} 页`,
  en: (page, total) => `Page ${page} of ${total}`,
}
