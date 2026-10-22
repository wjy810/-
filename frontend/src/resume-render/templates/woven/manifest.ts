import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  "id": "woven",
  "revision": 2,
  "name": "绢纹",
  "nameEn": "Woven",
  "category": "design",
  "summary": "织物侧边、衬线标题与暖灰留白，细节安静而精致。",
  "bestFor": "时尚、文创、品牌与内容策划",
  "tags": [
    "单栏",
    "艺术装饰",
    "照片可选"
  ],
  "maxPages": 2,
  "locale": "zh-CN",
  "paperSizes": [
    "A4"
  ],
  "fontPairings": [
    "serif",
    "mixed"
  ],
  "palettes": [
    {
      "id": "taupe",
      "name": "暖灰",
      "accent": "#6c5b50",
      "art": "woven-strip"
    },
    {
      "id": "mulberry",
      "name": "桑紫",
      "accent": "#70506a",
      "art": "woven-strip"
    },
    {
      "id": "moss",
      "name": "苔绿",
      "accent": "#4d6651",
      "art": "woven-strip"
    }
  ],
  "headerVariants": [
    {
      "id": "standard",
      "name": "标准"
    },
    {
      "id": "compact",
      "name": "紧凑"
    }
  ],
  "photo": "optional",
  "regions": [
    {
      "id": "main",
      "sections": [
        "summary",
        "experience",
        "projects",
        "education",
        "organizations",
        "skills",
        "certificates",
        "honors",
        "languages"
      ]
    }
  ],
  "atsLevel": "standard",
  "decorations": true,
  "defaults": {
    "fontPairing": "serif",
    "contactIcons": false
  }
}
