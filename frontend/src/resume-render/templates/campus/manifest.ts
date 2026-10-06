import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  id: 'campus',
  revision: 1,
  name: '校园',
  nameEn: 'Campus',
  category: 'industry',
  summary: '教育经历置顶，荣誉与社团醒目，技能以标签呈现，清爽有朝气。',
  bestFor: '应届生、实习与校园招聘',
  tags: ['单栏', '校招', '照片'],
  maxPages: 2,
  locale: 'zh-CN',
  paperSizes: ['A4'],
  fontPairings: ['sans', 'mixed'],
  palettes: [
    { id: 'teal', name: '青绿', accent: '#0f766e' },
    { id: 'sky', name: '晴蓝', accent: '#2563eb' },
    { id: 'orange', name: '橙', accent: '#c2410c' },
    { id: 'berry', name: '莓红', accent: '#be185d' },
  ],
  headerVariants: [{ id: 'split', name: '照片在右' }, { id: 'plain', name: '无照片' }],
  photo: 'optional',
  regions: [{ id: 'main', sections: ['summary', 'education', 'experience', 'projects', 'organizations', 'honors', 'skills', 'certificates', 'languages'] }],
  atsLevel: 'standard',
  decorations: false,
  sectionTitles: { experience: '实习经历', organizations: '校园经历' },
  // Students need one page: denser rhythm than the general templates.
  defaults: { lineHeight: 'COMPACT', spacing: 'TIGHT', photo: { mode: 'AUTO', shape: 'ROUNDED' }, sectionOrder: ['summary', 'education', 'experience', 'projects', 'organizations', 'honors', 'skills', 'certificates', 'languages'] },
}
