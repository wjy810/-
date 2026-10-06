import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  id: 'ledger',
  revision: 1,
  name: '分栏',
  nameEn: 'Ledger',
  category: 'modern',
  summary: '主栏讲经历，右侧事实栏列技能、教育与证书，中间一条发丝线。',
  bestFor: '产品经理、咨询、综合管理等需要兼顾经历与能力的岗位',
  tags: ['两栏', '现代', '照片'],
  maxPages: 2,
  locale: 'zh-CN',
  paperSizes: ['A4'],
  fontPairings: ['sans', 'mixed'],
  palettes: [
    { id: 'royal', name: '深蓝', accent: '#1e3a8a' },
    { id: 'forest', name: '墨绿', accent: '#14532d' },
    { id: 'grape', name: '葡萄紫', accent: '#5b21b6' },
  ],
  headerVariants: [{ id: 'split', name: '左右分栏' }, { id: 'photo', name: '带照片' }],
  photo: 'optional',
  regions: [
    { id: 'main', sections: ['summary', 'experience', 'projects', 'organizations'] },
    { id: 'facts', sections: ['skills', 'education', 'certificates', 'honors', 'languages'] },
  ],
  atsLevel: 'standard',
  decorations: false,
  defaults: { sectionOrder: ['summary', 'experience', 'projects', 'organizations', 'skills', 'education', 'certificates', 'honors', 'languages'] },
}
