import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  id: 'timeline',
  revision: 1,
  name: '时间轴',
  nameEn: 'Timeline',
  category: 'modern',
  summary: '左侧日期列与贯穿的时间线，让成长路径一目了然。',
  bestFor: '经历丰富、晋升路径清晰，或需要突出时间连续性的求职者',
  tags: ['单栏', '时间线', '照片'],
  maxPages: 2,
  locale: 'zh-CN',
  paperSizes: ['A4'],
  fontPairings: ['sans', 'mixed'],
  palettes: [
    { id: 'indigo', name: '靛蓝', accent: '#4338ca' },
    { id: 'teal', name: '青', accent: '#0e7490' },
    { id: 'coral', name: '珊瑚', accent: '#c2410c' },
  ],
  headerVariants: [{ id: 'split', name: '照片在右' }, { id: 'plain', name: '无照片' }],
  photo: 'optional',
  regions: [{ id: 'main', sections: ['summary', 'experience', 'projects', 'education', 'organizations', 'skills', 'certificates', 'honors', 'languages'] }],
  atsLevel: 'standard',
  decorations: false,
  defaults: { sectionOrder: ['summary', 'experience', 'projects', 'education', 'organizations', 'skills', 'certificates', 'honors', 'languages'] },
}
