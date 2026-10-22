import type { TemplateManifest } from '../manifest'

export const manifest: TemplateManifest = {
  "id": "consultant",
  "revision": 2,
  "name": "顾问",
  "nameEn": "Consultant",
  "category": "industry",
  "summary": "A compact single column with aligned organisations, dates and project outcomes.",
  "bestFor": "咨询、战略、海外商务与专业服务",
  "tags": [
    "单栏",
    "清晰排版",
    "英文"
  ],
  "maxPages": 2,
  "locale": "en",
  "paperSizes": [
    "A4",
    "LETTER"
  ],
  "fontPairings": [
    "serif",
    "mixed"
  ],
  "palettes": [
    {
      "id": "navy",
      "name": "Navy",
      "accent": "#253f64"
    },
    {
      "id": "burgundy",
      "name": "Burgundy",
      "accent": "#793f4c"
    },
    {
      "id": "graphite",
      "name": "Graphite",
      "accent": "#404750"
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
        "education",
        "organizations",
        "skills",
        "certificates",
        "honors",
        "languages"
      ]
    }
  ],
  "atsLevel": "strict",
  "decorations": false,
  "sectionTitles": {
    "projects": "Selected Projects"
  },
  "defaults": {
    "fontPairing": "serif",
    "contactIcons": false,
    "spacing": "TIGHT",
    "lineHeight": "COMPACT"
  }
}
