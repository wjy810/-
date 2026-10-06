import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  id: 'engineer',
  revision: 1,
  name: '工程师',
  nameEn: 'Engineer',
  category: 'industry',
  summary: '项目优先、技术栈分组、等宽字体点缀，链接一眼可见。',
  bestFor: '研发、测试、数据、算法等技术岗位',
  tags: ['单栏', '技术', '链接'],
  maxPages: 2,
  locale: 'zh-CN',
  paperSizes: ['A4'],
  fontPairings: ['tech', 'sans'],
  palettes: [
    { id: 'teal', name: '石墨青', accent: '#0f766e' },
    { id: 'night', name: '夜蓝', accent: '#1d4ed8' },
    { id: 'violet', name: '紫', accent: '#6d28d9' },
  ],
  headerVariants: [{ id: 'split', name: '左右分栏' }, { id: 'stacked', name: '上下排列' }],
  photo: 'none',
  regions: [{ id: 'main', sections: ['summary', 'skills', 'projects', 'experience', 'education', 'organizations', 'certificates', 'honors', 'languages'] }],
  atsLevel: 'standard',
  decorations: false,
  sectionTitles: { skills: '技术栈' },
  defaults: { sectionOrder: ['summary', 'skills', 'experience', 'projects', 'education', 'organizations', 'certificates', 'honors', 'languages'] },
}
