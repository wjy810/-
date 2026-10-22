import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  "id": "constellation",
  "revision": 2,
  "name": "星图",
  "nameEn": "Constellation",
  "category": "design",
  "summary": "深蓝页头与细金星盘构成视觉中心，正文与能力栏以清晰的线序展开。",
  "bestFor": "科技创意、交互设计、数字产品",
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
    "tech",
    "sans"
  ],
  "palettes": [
    {
      "id": "midnight",
      "name": "午夜蓝",
      "accent": "#294a73"
    },
    {
      "id": "plum",
      "name": "星紫",
      "accent": "#624b7c"
    },
    {
      "id": "petrol",
      "name": "深海",
      "accent": "#28656a"
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
  "decorations": true,
  "defaults": {
    "fontPairing": "tech",
    "contactIcons": false
  }
}
