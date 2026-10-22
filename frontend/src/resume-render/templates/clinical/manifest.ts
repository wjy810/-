import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  "id": "clinical",
  "revision": 2,
  "name": "诊疗·青禾",
  "nameEn": "Clinical",
  "category": "industry",
  "summary": "经历与教育资质分区，青绿色标题帮助快速定位专业信息。",
  "bestFor": "医疗、护理、健康服务与专业支持",
  "tags": [
    "双栏",
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
      "id": "jade",
      "name": "青禾",
      "accent": "#277267"
    },
    {
      "id": "blue",
      "name": "医蓝",
      "accent": "#346a8c"
    },
    {
      "id": "sage",
      "name": "草木",
      "accent": "#586a44"
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
        "organizations"
      ]
    },
    {
      "id": "side",
      "sections": [
        "education",
        "certificates",
        "skills",
        "honors",
        "languages"
      ]
    }
  ],
  "atsLevel": "standard",
  "decorations": false,
  "sectionTitles": {
    "summary": "专业概述"
  },
  "defaults": {
    "fontPairing": "sans",
    "contactIcons": true
  }
}
