import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  "id": "porcelain",
  "revision": 2,
  "name": "瓷青",
  "nameEn": "Porcelain",
  "category": "design",
  "summary": "青瓷花枝点缀页边，细框姓名区与留白章节兼具温度和分寸。",
  "bestFor": "文化、出版、国风品牌与教育",
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
    "mixed",
    "sans"
  ],
  "palettes": [
    {
      "id": "cobalt",
      "name": "瓷蓝",
      "accent": "#345d8a",
      "art": "porcelain-branch"
    },
    {
      "id": "celadon",
      "name": "青瓷",
      "accent": "#3f6b61",
      "art": "porcelain-branch"
    },
    {
      "id": "ink",
      "name": "墨青",
      "accent": "#425568",
      "art": "porcelain-branch"
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
    "fontPairing": "mixed",
    "contactIcons": false
  }
}
