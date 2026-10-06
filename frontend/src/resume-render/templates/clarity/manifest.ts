import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  id: 'clarity',
  revision: 1,
  name: '清晰',
  nameEn: 'Clarity',
  category: 'steady',
  summary: '单栏、无装饰、层级分明，为招聘系统筛选而生。',
  bestFor: '网申、外企与大型企业的 ATS 筛选，海投时的默认版本',
  tags: ['单栏', 'ATS 优先', '无照片'],
  maxPages: 2,
  locale: 'zh-CN',
  paperSizes: ['A4'],
  fontPairings: ['sans', 'serif'],
  palettes: [
    { id: 'graphite', name: '石墨', accent: '#2b2f36' },
    { id: 'navy', name: '海军蓝', accent: '#1d4e89' },
    { id: 'forest', name: '墨绿', accent: '#1f5f4a' },
  ],
  headerVariants: [{ id: 'left', name: '左对齐' }, { id: 'centered', name: '居中' }],
  photo: 'none',
  regions: [{ id: 'main', sections: ['summary', 'experience', 'projects', 'education', 'organizations', 'skills', 'certificates', 'honors', 'languages'] }],
  atsLevel: 'strict',
  decorations: false,
  defaults: { contactIcons: false },
}
