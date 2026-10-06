import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  id: 'banker',
  revision: 1,
  name: '投行',
  nameEn: 'Banker',
  category: 'industry',
  summary: '高密度单页：机构与地点、职位与日期两行严格对齐，信息量最大化。',
  bestFor: '投行、券商、四大、咨询与律所，要求严格一页的申请',
  tags: ['单页', '高密度', '衬线'],
  maxPages: 1,
  locale: 'zh-CN',
  paperSizes: ['A4'],
  fontPairings: ['serif', 'sans'],
  palettes: [
    { id: 'navy', name: '海军蓝', accent: '#102a43' },
    { id: 'charcoal', name: '炭黑', accent: '#1f2328' },
    { id: 'burgundy', name: '勃艮第', accent: '#6b1e2e' },
  ],
  headerVariants: [{ id: 'centered', name: '居中' }],
  photo: 'none',
  regions: [{ id: 'main', sections: ['summary', 'education', 'experience', 'projects', 'organizations', 'skills', 'certificates', 'honors', 'languages'] }],
  atsLevel: 'strict',
  decorations: false,
  defaults: {
    fontSize: 'S', spacing: 'TIGHT', pageMargin: 'NARROW', pageTarget: 'ONE', contactIcons: false,
    sectionOrder: ['summary', 'education', 'experience', 'projects', 'organizations', 'skills', 'certificates', 'honors', 'languages'],
  },
}
