import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  id: 'harvard',
  revision: 1,
  name: 'Harvard',
  nameEn: 'Harvard',
  category: 'steady',
  summary: 'The classic one-page English format: centred name, ruled small-caps headings, dates on the right.',
  bestFor: '英文简历：外企、海外岗位、留学与交换申请',
  tags: ['英文', '单栏', 'Letter'],
  maxPages: 2,
  locale: 'en',
  paperSizes: ['A4', 'LETTER'],
  fontPairings: ['serif', 'sans'],
  palettes: [
    { id: 'black', name: '黑', accent: '#111111' },
    { id: 'midnight', name: '深蓝', accent: '#102a43' },
  ],
  headerVariants: [{ id: 'centered', name: '居中' }],
  photo: 'none',
  regions: [{ id: 'main', sections: ['summary', 'education', 'experience', 'projects', 'organizations', 'skills', 'certificates', 'honors', 'languages'] }],
  atsLevel: 'strict',
  decorations: false,
  defaults: { contactIcons: false, sectionOrder: ['summary', 'education', 'experience', 'projects', 'organizations', 'skills', 'certificates', 'honors', 'languages'] },
}
