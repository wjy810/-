import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  id: 'editorial',
  revision: 1,
  name: '纸本',
  nameEn: 'Editorial',
  category: 'design',
  summary: '真实纸张纹理、衬线大字与编号章节，像一本排版讲究的小册子。',
  bestFor: '文字、编辑、品牌、咨询与人文社科方向',
  tags: ['单栏', '纸纹', '衬线'],
  maxPages: 2,
  locale: 'zh-CN',
  paperSizes: ['A4'],
  fontPairings: ['mixed', 'serif'],
  palettes: [
    { id: 'ivory', name: '暖纸', accent: '#9a3b2e', ink: '#2b2a28', paper: '#fbf8f2', art: 'paper-warm' },
    { id: 'frost', name: '冷纸', accent: '#2f5d8a', ink: '#1e2a3a', paper: '#f7f8fa', art: 'paper-cool' },
    { id: 'linen', name: '亚麻', accent: '#6b5b3e', ink: '#33302b', paper: '#f8f6f1', art: 'paper-linen' },
  ],
  headerVariants: [{ id: 'masthead', name: '刊头' }, { id: 'left', name: '左对齐' }],
  photo: 'none',
  regions: [{ id: 'main', sections: ['summary', 'experience', 'projects', 'education', 'organizations', 'skills', 'certificates', 'honors', 'languages'] }],
  atsLevel: 'standard',
  decorations: true,
  defaults: { pageMargin: 'WIDE' },
}
