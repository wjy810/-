import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  id: 'bold',
  revision: 1,
  name: '撞色',
  nameEn: 'Bold',
  category: 'modern',
  summary: '通栏强调色页头与白色姓名，第一眼就被记住。',
  bestFor: '销售、市场、商务拓展等需要留下深刻印象的岗位',
  tags: ['单栏', '色块页头', '照片'],
  maxPages: 2,
  locale: 'zh-CN',
  paperSizes: ['A4'],
  fontPairings: ['sans', 'mixed'],
  palettes: [
    { id: 'deep-sea', name: '深海', accent: '#0f3d5e' },
    { id: 'evergreen', name: '墨绿', accent: '#1f4d3a' },
    { id: 'brick', name: '砖红', accent: '#8c2f1c' },
    { id: 'night', name: '夜黑', accent: '#111827' },
  ],
  headerVariants: [{ id: 'band', name: '通栏色块' }, { id: 'tall', name: '加高色块' }],
  photo: 'optional',
  regions: [{ id: 'main', sections: ['summary', 'experience', 'projects', 'education', 'organizations', 'skills', 'certificates', 'honors', 'languages'] }],
  atsLevel: 'standard',
  decorations: false,
  defaults: { photo: { mode: 'AUTO', shape: 'SQUARE' } },
}
