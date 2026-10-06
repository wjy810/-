# 插画提示词记录

所有插画由 `generate.py` 通过 OpenAI 兼容的图像接口生成（透明底 PNG），再由 `export.py` 裁边、缩放并转为 WebP 供前端使用。

## 统一风格提示词

> Soft 3D clay illustration, rounded chunky friendly shapes, smooth matte clay material, soft diffused studio lighting with gentle contact shadow, pastel palette of ink indigo (#5E59E8), soft lavender (#CBCAFB), warm apricot orange (#FA8C55) and creamy off-white paper (#F6F1EA), subtle mint green accents only when needed, isolated subject centered with generous empty padding, transparent background, no text, no letters, no numbers, no logos, no watermark, clean and premium.

## 素材清单

| 名称 | 尺寸 | 主体描述 | 用途 |
| --- | --- | --- | --- |
| `hero-resume` | 1024x1536 | a tall portrait resume paper sheet standing upright and slightly tilted, with a round avatar badge at the top left, several rounded indigo and lavender text-line bars, three bullet dots, and a small apricot bookmark ribbon hanging from the top right corner | 首页主视觉子元素 |
| `hero-check` | 1024x1024 | a round shield-shaped badge with a bold thick checkmark, indigo shield body with an apricot rim, slightly tilted, glossy highlight | 首页主视觉子元素 |
| `hero-magnifier` | 1024x1024 | a magnifying glass with a chunky apricot handle and a glossy slightly lavender-tinted lens, tilted 30 degrees | 首页主视觉子元素 |
| `hero-chat` | 1024x1024 | a rounded speech bubble in soft lavender with three indigo dots inside, and a small apricot four-pointed sparkle star at its top right | 首页主视觉子元素 |
| `hero-target` | 1024x1024 | a small archery target with rings in indigo, cream and apricot, with an indigo arrow hitting the bullseye, slightly turned to the side | 首页主视觉子元素 |
| `hero-sparkles` | 1024x1024 | a loose cluster of three four-pointed sparkle stars of different sizes, one apricot, one lavender, one cream, floating | 首页主视觉子元素 |
| `hero-plane` | 1024x1024 | a folded paper airplane in creamy white with indigo folded edges, flying upward to the right | 首页主视觉子元素 |
| `hero-cap` | 1024x1024 | a graduation mortarboard cap in ink indigo with an apricot tassel, slightly tilted | 首页主视觉子元素 |
| `hero-pencil` | 1024x1024 | a chunky short pencil with an apricot body, cream wood tip and an indigo eraser, lying diagonally | 首页主视觉子元素 |
| `empty-resume` | 1024x1024 | a blank creamy sheet of paper lying slightly tilted with a chunky apricot pencil resting across it and a tiny lavender sparkle above | 空状态 |
| `empty-match` | 1024x1024 | two small document cards overlapping, one indigo-accented and one apricot-accented, with a magnifying glass hovering over where they overlap | 空状态 |
| `empty-canvas` | 1024x1024 | a folded paper map in cream with a dotted indigo path leading to an apricot location pin, and a small compass beside it | 空状态 |
| `empty-interview` | 1024x1024 | a retro studio microphone in indigo with an apricot grille band on a small stand, and a little lavender speech bubble floating beside it | 空状态 |
| `empty-notification` | 1024x1024 | a calm notification bell in apricot resting on a soft lavender cushion, with a tiny cream crescent moon floating above it | 空状态 |
| `empty-folder` | 1024x1024 | an open indigo folder with a few creamy paper sheets peeking out and a small apricot star sticker on the front | 空状态 |
| `empty-search` | 1024x1024 | a magnifying glass with an apricot handle looking at an empty dotted lavender circle on the ground | 空状态 |
| `empty-templates` | 1024x1024 | a fan of three resume template cards spread out, each with a different header color: indigo, apricot and lavender, with simple line bars | 空状态 |
| `error-plane` | 1024x1024 | a gently crumpled paper airplane that has landed nose-down, with a small apricot adhesive bandage on its wing, a few tiny lavender dust puffs | 错误 / 404 |
| `identity-student` | 1024x1024 | a cute school backpack in ink indigo with apricot straps and zipper pulls, a small cream notebook peeking out | 建档身份选择 |
| `identity-graduate` | 1024x1024 | a rolled diploma scroll tied with an apricot ribbon resting against an indigo graduation cap | 建档身份选择 |
| `identity-professional` | 1024x1024 | a modern rounded briefcase in ink indigo with an apricot handle and cream clasp | 建档身份选择 |
| `ai-orb` | 1024x1024 | a smooth glossy sphere with a soft indigo to violet to apricot gradient, with a small cream four-pointed sparkle star on its upper right, like a friendly AI assistant avatar | AI 助手头像 |
| `auth-key` | 1024x1024 | a chunky key with a plain round apricot bow (no face, no eyes, no decorations) and an indigo blade, floating next to a small open lavender padlock | 登录弹层 |
| `welcome-desk` | 1536x1024 | a cozy tiny desk scene: an open laptop whose screen shows a resume layout with indigo bars, a cream coffee mug, a small potted plant with rounded leaves, a stack of two notebooks in apricot and lavender, and a few sparkles floating above | 工作台欢迎区 |
| `dash-match` | 1024x1024 | an archery target in indigo and cream rings with an apricot arrow in the center | 工作台 / 模块入口 |
| `dash-planning` | 1024x1024 | a small rounded signpost with three arrow boards in indigo, apricot and lavender pointing different directions on a little grassy mint base | 工作台 / 模块入口 |
| `dash-interview` | 1024x1024 | two overlapping speech bubbles, a larger indigo one and a smaller apricot one, with a tiny sparkle | 工作台 / 模块入口 |
| `success-trophy` | 1024x1024 | a small rounded trophy cup in apricot with an indigo base and a cream star emblem, a few lavender sparkles around it | 成功反馈 |
| `success-confetti` | 1024x1024 | a party popper cone in indigo bursting with rounded confetti pieces and ribbons in apricot, lavender and cream | 成功反馈 |
| `ink-pen` | 1024x1024 | an elegant chunky fountain pen with an ink indigo body and an apricot gold nib, resting diagonally on a small cream paper card that has a drawn indigo checkmark stroke | 首页原则区 |
| `rocket` | 1024x1024 | a small rounded rocket in cream with an indigo nose cone and fins, an apricot porthole rim, and a soft lavender cloud puff trail, launching upward to the right | 引导 / 启动 |
| `dash-library` | 1024x1024 | a neat stack of three closed books and a folder in indigo, apricot and lavender with a small bookmark | 工作台 / 模块入口 |

## 重新生成

```sh
JP_IMAGE_API_BASE=https://<host>/v1 JP_IMAGE_API_KEY=<key> python3 design/illustrations/generate.py hero-resume --force
python3 design/illustrations/export.py
```

密钥只通过环境变量传入，不写入仓库。
