import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  "id": "arcade",
  "revision": 2,
  "name": "拱廊",
  "nameEn": "Arcade",
  "category": "design",
  "summary": "拱形刊头与陶土细线呼应，柔和纸纹承托清晰的单栏经历。",
  "bestFor": "建筑、空间、艺术与策展方向",
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
      "id": "clay",
      "name": "陶土",
      "accent": "#974f37",
      "art": "paper-cotton"
    },
    {
      "id": "umber",
      "name": "褐石",
      "accent": "#6b5847",
      "art": "paper-cotton"
    },
    {
      "id": "slate",
      "name": "岩蓝",
      "accent": "#3c596d",
      "art": "paper-cotton"
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
