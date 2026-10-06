import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  id: 'classic',
  revision: 1,
  name: '经典',
  nameEn: 'Classic',
  category: 'steady',
  summary: '居中衬线姓名、字距标题与通栏细线，克制而郑重。',
  bestFor: '国企、事业单位、传统行业，或任何不确定风格时的稳妥选择',
  tags: ['单栏', 'ATS 友好', '衬线'],
  maxPages: 2,
  locale: 'zh-CN',
  paperSizes: ['A4'],
  fontPairings: ['serif', 'mixed', 'sans'],
  palettes: [
    { id: 'ink', name: '墨黑', accent: '#1f2328' },
    { id: 'navy', name: '藏青', accent: '#1e3a5f' },
    { id: 'wine', name: '酒红', accent: '#7a2e3a' },
  ],
  headerVariants: [{ id: 'centered', name: '居中' }, { id: 'left', name: '左对齐' }],
  photo: 'none',
  regions: [{ id: 'main', sections: ['summary', 'education', 'experience', 'projects', 'organizations', 'skills', 'certificates', 'honors', 'languages'] }],
  atsLevel: 'strict',
  decorations: false,
  defaults: {},
}
