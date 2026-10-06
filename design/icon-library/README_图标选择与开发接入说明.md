# JobProof AI 全栏目 SVG 图标库 v1

## 交付内容

- 11 个正式主栏目，每个栏目 8 个候选：共 88 个主导航 SVG。
- 28 个公共操作 SVG：覆盖新建画布、学习计划、能力验证、版本记录、节点操作、筛选、上传、提醒等。
- 总计 116 个独立 SVG，另含 SVG Sprite、TypeScript 路径映射、状态 CSS、JSON 清单、交互式 HTML 总览与 3 张总览图。

## 正式主栏目

1. 工作台
2. AI 对话
3. 求职资料库
4. AI 职业规划
5. 岗位匹配
6. 简历工作台
7. 求职材料包
8. 模拟面试
9. 笔试训练场
10. 模板中心
11. 通知中心

投递管理当前暂不进入主导航；风险审核、求职复盘、机会对比仍为待定模块，因此未混入本版正式栏目图标。

## 选型建议

- 每个文件夹的 A 款是语义最直接、在 20～24 px 下辨识度最稳定的推荐首选。
- B～H 款不是简单换色，而是不同的视觉隐喻，可根据页面气质选择。
- 建议主导航统一只使用一套线性图标，不要在同一侧边栏混用实心、彩色和线性图标。
- AI 职业规划推荐 A「职业能力树」，它最能体现“多能力画布 → 学习计划 → 能力验证 → 版本记录”的项目差异点。

## 前端使用

独立 SVG 使用 `currentColor`，把颜色设置在父元素即可：

```html
<button class="nav-item" aria-current="page">
  <img class="nav-icon" src="/icons/01_主栏目图标_每栏8选1/04_AI职业规划/A_职业能力树.svg" alt="">
  <span>AI 职业规划</span>
</button>
```

如需让外链 SVG 跟随 `currentColor`，推荐由构建工具作为组件导入；若使用普通 `<img>`，浏览器不会把父元素颜色传入文件内部。React/Vue 项目可用 SVGR、vite-svg-loader 或直接内联 `<svg>`。

Sprite 用法：

```html
<svg class="nav-icon" aria-hidden="true">
  <use href="/icons/jobproof-icons-sprite.svg#career-planning-a"></use>
</svg>
```

## 尺寸与状态

| 场景 | 推荐尺寸 | 颜色 |
|---|---:|---|
| 桌面左侧导航 | 20 px | 普通 `#64748B`，选中 `#2F6BFF` |
| 移动端底部导航 | 22～24 px | 普通 `#718096`，选中 `#2F6BFF` |
| 卡片入口 | 28～32 px | 品牌蓝或栏目主题色 |
| 空状态插图 | 48～64 px | 可配浅蓝背景容器 |

统一规范：`viewBox="0 0 24 24"`、描边 `1.8`、圆角端点、无固定背景、颜色使用 `currentColor`。

## 文件结构

```text
01_主栏目图标_每栏8选1/
  01_工作台/A_网格总览.svg ... H_首页罗盘.svg
  ...
02_公共操作图标/
03_开发接入/
  jobproof-icons-sprite.svg
  icon-manifest.json
  icon-map.ts
  icon-states.css
04_图标总览/
  交互式图标总览.html
  01～03 总览 SVG/PNG
```
