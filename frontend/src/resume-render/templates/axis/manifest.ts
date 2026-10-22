import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  "id": "axis",
  "revision": 2,
  "name": "轴线",
  "nameEn": "Axis",
  "category": "modern",
  "summary": "章节构成左侧索引，经历正文沿统一轴线展开，阅读节奏明晰。",
  "bestFor": "综合管理、专业服务与经验型求职",
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
      "id": "graphite",
      "name": "石墨",
      "accent": "#3b4857"
    },
    {
      "id": "navy",
      "name": "藏蓝",
      "accent": "#315f86"
    },
    {
      "id": "brick",
      "name": "砖红",
      "accent": "#944e49"
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
