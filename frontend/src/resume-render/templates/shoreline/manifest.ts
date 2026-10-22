import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  "id": "shoreline",
  "revision": 2,
  "name": "海岸",
  "nameEn": "Shoreline",
  "category": "design",
  "summary": "横向联系区、海蓝短线与轻盈浪纹，舒展的单栏叙事。",
  "bestFor": "文旅、服务、传播与公共关系",
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
    "sans",
    "mixed"
  ],
  "palettes": [
    {
      "id": "ocean",
      "name": "海蓝",
      "accent": "#27657f",
      "art": "shoreline-waves"
    },
    {
      "id": "lagoon",
      "name": "潟湖",
      "accent": "#306e66",
      "art": "shoreline-waves"
    },
    {
      "id": "dusk",
      "name": "暮色",
      "accent": "#59547b",
      "art": "shoreline-waves"
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
    "fontPairing": "sans",
    "contactIcons": false
  }
}
