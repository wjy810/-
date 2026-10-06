import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  id: 'meridian',
  revision: 1,
  name: '子午',
  nameEn: 'Meridian',
  category: 'modern',
  summary: '左侧浅色侧栏承载照片、联系方式与技能，主栏专注经历。',
  bestFor: '互联网、运营、产品、市场等需要一眼看清能力与经历的岗位',
  tags: ['两栏', '照片', '现代'],
  maxPages: 2,
  locale: 'zh-CN',
  paperSizes: ['A4'],
  fontPairings: ['sans', 'mixed'],
  palettes: [
    { id: 'fog-blue', name: '雾蓝', accent: '#3b6a9e' },
    { id: 'pine', name: '松绿', accent: '#2f6b5b' },
    { id: 'graphite', name: '石墨', accent: '#3a3f47' },
    { id: 'ochre', name: '赭石', accent: '#8a5a36' },
  ],
  headerVariants: [{ id: 'stacked', name: '照片在上' }, { id: 'compact', name: '紧凑' }],
  photo: 'optional',
  regions: [
    { id: 'main', sections: ['summary', 'experience', 'projects', 'education', 'organizations'] },
    { id: 'side', sections: ['skills', 'languages', 'certificates', 'honors'] },
  ],
  atsLevel: 'standard',
  decorations: false,
  defaults: { sectionOrder: ['summary', 'experience', 'projects', 'education', 'organizations', 'skills', 'languages', 'certificates', 'honors'] },
}
