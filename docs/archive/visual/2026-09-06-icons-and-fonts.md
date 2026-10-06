# 图标与字体核验

日期：2026-09-06。仅访问本地 `http://127.0.0.1:5174`；不改变导航、图标命名或线上数据。

## 图标

- 运行 `frontend/scripts/icon-acceptance.mjs`，直接加载 Vite 编译的 `JobProofIcon.vue`、类型目录和真实外部 SVG Sprite，不复制绘制逻辑或增加产品路由。
- 目录共 249 个名称；每个检查 16、20、24px 深色和 24px 品牌蓝，共 996 个组合。
- 所有组合都有非空图形像素、正确的尺寸、装饰性辅助属性和对应 Sprite ID；品牌蓝像素验证 `currentColor` 实际生效，不仅断言属性字符串。
- 七张图表已逐张查看，未见图形缺失、明显裁切或光学位置失衡；复杂业务图标在 16px 仍受像素数量限制，不据此替换为另一套图标系统。
- 未知名称不渲染。最终图表运行无资源 HTTP 错误、控制台错误或页面异常。
- 身份探测 `/api/v1/me` 使用明确的未登录模拟响应，避免无关的预期 401 干扰纯图标检查；组件与所有图形资源仍为真实加载。这个检查不作为登录验收。

证据目录：`.codex_tmp/icon-acceptance-20260906/`，包含 `results.json` 和 `atlas-1.png` 至 `atlas-7.png`。命令使用 Node 24.19.0 与现有 JavaScript Playwright 依赖；本机捆绑 Python 没有 Playwright，未另装一套浏览器测试工具。

## 字体

对 `frontend/src`、`frontend/public`、`backend/src/main/resources` 查找 `.woff/.woff2/.ttf/.otf`：前端没有独立字体二进制文件；现有系统字体回退栈不产生额外下载。未为了“清理”而删除系统字体名称或修改简历排版。

后端的两份字体都有明确运行时消费者：

| 资源 | 消费者 |
| --- | --- |
| `backend/src/main/resources/fonts/NotoSansSC-Regular.ttf` | `ResumePdfRenderer.java:25`、`ResumeStructuredPdfRenderer.java:22` |
| `backend/src/main/resources/fonts/NotoSerifSC-Variable.ttf` | `ResumeStructuredPdfRenderer.java:23` |

因此，本次没有发现可以安全删除的发布字体资源。`node_modules`、历史交付资料和参考项目不属于应用字体清理范围。
