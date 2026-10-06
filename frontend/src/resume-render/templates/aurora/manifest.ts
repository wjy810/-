import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  id: 'aurora',
  revision: 1,
  name: '极光',
  nameEn: 'Aurora',
  category: 'modern',
  summary: '右上角一抹柔和极光，细体大字姓名，轻盈而有设计感。',
  bestFor: '设计、新媒体、品牌与创意类岗位',
  tags: ['单栏', '装饰', '照片'],
  maxPages: 2,
  locale: 'zh-CN',
  paperSizes: ['A4'],
  fontPairings: ['sans', 'mixed'],
  palettes: [
    { id: 'stardust', name: '星紫', accent: '#6d28d9', art: 'aurora-soft' },
    { id: 'clear-sky', name: '晴蓝', accent: '#2563eb', art: 'aurora-soft' },
    { id: 'peach', name: '蜜桃', accent: '#c2410c', art: 'aurora-soft' },
  ],
  headerVariants: [{ id: 'airy', name: '舒展' }, { id: 'photo', name: '带照片' }],
  photo: 'optional',
  regions: [{ id: 'main', sections: ['summary', 'experience', 'projects', 'education', 'organizations', 'skills', 'certificates', 'honors', 'languages'] }],
  atsLevel: 'standard',
  decorations: true,
  defaults: {},
}
