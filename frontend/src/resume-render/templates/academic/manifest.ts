import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  id: 'academic',
  revision: 1,
  name: '学术',
  nameEn: 'Academic',
  category: 'industry',
  summary: '学术 CV 体例：教育与科研优先，衬线排版，允许多页。',
  bestFor: '科研岗位、读研读博申请、高校与研究所求职',
  tags: ['单栏', '衬线', '多页'],
  maxPages: 3,
  locale: 'zh-CN',
  paperSizes: ['A4'],
  fontPairings: ['serif', 'mixed'],
  palettes: [
    { id: 'crimson', name: '学院红', accent: '#8b1e2d' },
    { id: 'royal', name: '学院蓝', accent: '#1e3a8a' },
    { id: 'ink', name: '墨', accent: '#222222' },
  ],
  headerVariants: [{ id: 'left', name: '左对齐' }, { id: 'centered', name: '居中' }],
  photo: 'optional',
  regions: [{ id: 'main', sections: ['summary', 'education', 'projects', 'experience', 'honors', 'organizations', 'skills', 'certificates', 'languages'] }],
  atsLevel: 'standard',
  decorations: false,
  sectionTitles: { summary: '研究概述', projects: '科研项目' },
  defaults: { sectionOrder: ['summary', 'education', 'projects', 'experience', 'honors', 'organizations', 'skills', 'certificates', 'languages'] },
}
