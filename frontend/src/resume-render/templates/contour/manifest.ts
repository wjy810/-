import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  id: 'contour',
  revision: 1,
  name: '等高线',
  nameEn: 'Contour',
  category: 'design',
  summary: '角落的等高线与等宽编号，冷静、理性，带一点探索感。',
  bestFor: '工程、地理、户外、能源与研究类岗位',
  tags: ['单栏', '线描', '技术感'],
  maxPages: 2,
  locale: 'zh-CN',
  paperSizes: ['A4'],
  fontPairings: ['tech', 'sans'],
  palettes: [
    { id: 'slate', name: '岩灰', accent: '#334155' },
    { id: 'lake', name: '湖蓝', accent: '#0f766e' },
    { id: 'rust', name: '锈红', accent: '#9a3412' },
  ],
  headerVariants: [{ id: 'left', name: '左对齐' }],
  photo: 'none',
  regions: [{ id: 'main', sections: ['summary', 'experience', 'projects', 'education', 'organizations', 'skills', 'certificates', 'honors', 'languages'] }],
  atsLevel: 'standard',
  decorations: true,
  defaults: {},
}
