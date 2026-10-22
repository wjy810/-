import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  "id": "prism",
  "revision": 2,
  "name": "棱镜",
  "nameEn": "Prism",
  "category": "modern",
  "summary": "切角姓名区与清晰色条，右侧能力栏让重点一眼可见。",
  "bestFor": "产品、互联网、项目协作岗位",
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
      "id": "cobalt",
      "name": "钴蓝",
      "accent": "#315eb1"
    },
    {
      "id": "teal",
      "name": "青绿",
      "accent": "#19736b"
    },
    {
      "id": "plum",
      "name": "紫晶",
      "accent": "#75538c"
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
        "organizations"
      ]
    },
    {
      "id": "side",
      "sections": [
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
    "contactIcons": true
  }
}
