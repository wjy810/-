import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  id: 'watercolor',
  revision: 1,
  name: '水彩',
  nameEn: 'Watercolor',
  category: 'design',
  summary: '页顶一道水彩晕染，温柔但不失专业，适合需要亲和力的岗位。',
  bestFor: '教育、医疗健康、设计、公益与服务类岗位',
  tags: ['单栏', '水彩', '照片'],
  maxPages: 2,
  locale: 'zh-CN',
  paperSizes: ['A4'],
  fontPairings: ['sans', 'mixed'],
  palettes: [
    { id: 'indigo', name: '靛青', accent: '#3a4f8a', art: 'wash-indigo' },
    { id: 'sage', name: '鼠尾草', accent: '#4a6b4f', art: 'wash-sage' },
    { id: 'terracotta', name: '赤陶', accent: '#9a4a2c', art: 'wash-terracotta' },
  ],
  headerVariants: [{ id: 'centered', name: '居中' }, { id: 'photo', name: '带照片' }],
  photo: 'optional',
  regions: [{ id: 'main', sections: ['summary', 'experience', 'projects', 'education', 'organizations', 'skills', 'certificates', 'honors', 'languages'] }],
  atsLevel: 'standard',
  decorations: true,
  defaults: {},
}
