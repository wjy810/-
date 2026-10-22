import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  "id": "botanica",
  "revision": 2,
  "name": "植笺",
  "nameEn": "Botanica",
  "category": "design",
  "summary": "植物线描轻落纸角，经历与能力分栏，清新而有秩序。",
  "bestFor": "教育、公益、生活方式与服务岗位",
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
    "mixed",
    "sans"
  ],
  "palettes": [
    {
      "id": "sage",
      "name": "鼠尾草",
      "accent": "#42654b",
      "art": "botanical-sprig"
    },
    {
      "id": "olive",
      "name": "橄榄",
      "accent": "#65602e",
      "art": "botanical-sprig"
    },
    {
      "id": "blue",
      "name": "雾蓝",
      "accent": "#345c75",
      "art": "botanical-sprig"
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
    "fontPairing": "mixed",
    "contactIcons": false
  }
}
