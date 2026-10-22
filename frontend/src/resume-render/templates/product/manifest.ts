import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  "id": "product",
  "revision": 2,
  "name": "产品·航标",
  "nameEn": "Product",
  "category": "industry",
  "summary": "项目优先、里程碑条目与技能侧栏，清楚呈现产品推进过程。",
  "bestFor": "产品经理、项目经理与业务分析",
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
      "id": "blue",
      "name": "航标蓝",
      "accent": "#285fa2"
    },
    {
      "id": "green",
      "name": "远航绿",
      "accent": "#326859"
    },
    {
      "id": "amber",
      "name": "琥珀",
      "accent": "#886026"
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
  "sectionTitles": {
    "projects": "产品与项目"
  },
  "defaults": {
    "fontPairing": "sans",
    "contactIcons": true
  }
}
