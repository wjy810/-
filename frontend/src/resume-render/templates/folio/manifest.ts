import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  "id": "folio",
  "revision": 2,
  "name": "折页",
  "nameEn": "Folio",
  "category": "modern",
  "summary": "通栏姓名与折页式分隔，经历和资质各有清晰位置。",
  "bestFor": "商务、运营、综合管理",
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
    "mixed",
    "sans"
  ],
  "palettes": [
    {
      "id": "navy",
      "name": "海军蓝",
      "accent": "#314f69"
    },
    {
      "id": "pine",
      "name": "松绿",
      "accent": "#3e6558"
    },
    {
      "id": "aubergine",
      "name": "茄紫",
      "accent": "#71506b"
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
        "skills",
        "certificates",
        "honors",
        "languages"
      ]
    }
  ],
  "atsLevel": "standard",
  "decorations": false,
  "defaults": {
    "fontPairing": "mixed",
    "contactIcons": false
  }
}
