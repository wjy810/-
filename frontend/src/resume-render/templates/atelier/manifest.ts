import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  "id": "atelier",
  "revision": 2,
  "name": "留白",
  "nameEn": "Atelier",
  "category": "design",
  "summary": "A generous nameplate, cotton paper and an asymmetric project-led layout.",
  "bestFor": "海外设计、创意工作室与品牌岗位",
  "tags": [
    "双栏",
    "艺术装饰",
    "英文"
  ],
  "maxPages": 2,
  "locale": "en",
  "paperSizes": [
    "A4",
    "LETTER"
  ],
  "fontPairings": [
    "mixed",
    "sans"
  ],
  "palettes": [
    {
      "id": "charcoal",
      "name": "Charcoal",
      "accent": "#343b39",
      "art": "paper-cotton"
    },
    {
      "id": "sienna",
      "name": "Sienna",
      "accent": "#92533e",
      "art": "paper-cotton"
    },
    {
      "id": "forest",
      "name": "Forest",
      "accent": "#3a6455",
      "art": "paper-cotton"
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
  "photo": "none",
  "regions": [
    {
      "id": "main",
      "sections": [
        "summary",
        "projects",
        "experience",
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
    "fontPairing": "mixed",
    "contactIcons": false
  }
}
