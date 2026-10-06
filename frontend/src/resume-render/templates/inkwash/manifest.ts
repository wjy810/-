import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  id: 'inkwash',
  revision: 1,
  name: '水墨',
  nameEn: 'Ink Wash',
  category: 'design',
  summary: '页脚远山、角落梅枝或修竹、朱红方印，东方意境点到为止。',
  bestFor: '文化、教育、传媒、国风品牌与设计相关岗位',
  tags: ['单栏', '国风', '衬线'],
  maxPages: 2,
  locale: 'zh-CN',
  paperSizes: ['A4'],
  fontPairings: ['serif', 'mixed'],
  palettes: [
    { id: 'ink', name: '墨', accent: '#a23b2c', ink: '#222222', art: 'ink-plum' },
    { id: 'indigo', name: '黛蓝', accent: '#a23b2c', ink: '#1f3a4d', art: 'ink-plum' },
    { id: 'bamboo', name: '竹青', accent: '#2e4a3f', ink: '#24302b', art: 'ink-bamboo' },
  ],
  headerVariants: [{ id: 'seal', name: '印章' }, { id: 'plain', name: '无印章' }],
  photo: 'optional',
  regions: [{ id: 'main', sections: ['summary', 'experience', 'projects', 'education', 'organizations', 'skills', 'certificates', 'honors', 'languages'] }],
  atsLevel: 'standard',
  decorations: true,
  defaults: {},
}
