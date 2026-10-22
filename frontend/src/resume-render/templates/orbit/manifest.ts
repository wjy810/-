import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  "id": "orbit",
  "revision": 2,
  "name": "环线",
  "nameEn": "Orbit",
  "category": "modern",
  "summary": "姓名首字标识、圆点章节与紧凑联系区，形成简洁的个人名片。",
  "bestFor": "通用求职、个人品牌与跨行业转型",
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
      "id": "teal",
      "name": "湖绿",
      "accent": "#23746b"
    },
    {
      "id": "blue",
      "name": "晴蓝",
      "accent": "#3668a0"
    },
    {
      "id": "berry",
      "name": "莓红",
      "accent": "#974861"
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
  "decorations": false,
  "defaults": {
    "fontPairing": "sans",
    "contactIcons": false
  }
}
