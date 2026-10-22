import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  "id": "mosaic",
  "revision": 2,
  "name": "构成",
  "nameEn": "Mosaic",
  "category": "design",
  "summary": "非对称姓名区与几何拼色，双栏展示经历和专业能力。",
  "bestFor": "视觉设计、创意策划、品牌传播",
  "tags": [
    "双栏",
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
      "id": "rust",
      "name": "赤陶",
      "accent": "#a14d35",
      "art": "paper-cut"
    },
    {
      "id": "teal",
      "name": "松石",
      "accent": "#246b69",
      "art": "paper-cut"
    },
    {
      "id": "ochre",
      "name": "赭金",
      "accent": "#82601e",
      "art": "paper-cut"
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
        "skills",
        "education",
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
