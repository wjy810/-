import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  "id": "growth",
  "revision": 2,
  "name": "增长·刻度",
  "nameEn": "Growth",
  "category": "industry",
  "summary": "编号项目与横向技能组，突出经历中的目标、行动和成果。",
  "bestFor": "市场、销售、增长与用户运营",
  "tags": [
    "单栏",
    "清晰排版",
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
      "id": "orange",
      "name": "燃橙",
      "accent": "#a65025"
    },
    {
      "id": "green",
      "name": "增长绿",
      "accent": "#2e6c4b"
    },
    {
      "id": "blue",
      "name": "洞察蓝",
      "accent": "#365d8d"
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
        "projects",
        "experience",
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
  "decorations": false,
  "sectionTitles": {
    "projects": "增长项目"
  },
  "defaults": {
    "fontPairing": "sans",
    "contactIcons": false
  }
}
