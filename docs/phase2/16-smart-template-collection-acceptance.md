# 16 套智能简历模板实施与验收报告

验收日期：2026-10-21（Asia/Shanghai）。项目：JobProof。

后续更正：用户反馈揭示了深色主题标题颜色缺陷及若干装饰／构图问题。第一轮视觉验收存在遗漏，模板已进行 revision 2 修订；视觉结论与最新结果以 `17-template-visual-review.md` 为准。本文保留第一轮实施与测试记录。

## 交付结果

内置模板由 16 套扩展至 32 套。新增 8 套设计感、4 套现代、4 套行业模板，其中 14 套中文、2 套英文。全库分类数量为稳健 3、现代 9、设计感 12、行业 8。

| 分类 | 模板 | ID | 语言 | 默认示例 PDF 页数 |
| --- | --- | --- | --- | ---: |
| 设计感 | 植笺 | botanica | 中文 | 1 |
| 设计感 | 拱廊 | arcade | 中文 | 2 |
| 设计感 | 绢纹 | woven | 中文 | 2 |
| 设计感 | 构成 | mosaic | 中文 | 1 |
| 设计感 | 海岸 | shoreline | 中文 | 2 |
| 设计感 | 瓷青 | porcelain | 中文 | 2 |
| 设计感 | 星图 | constellation | 中文 | 1 |
| 设计感 | 留白 Atelier | atelier | 英文 | 1 |
| 现代 | 棱镜 | prism | 中文 | 1 |
| 现代 | 轴线 | axis | 中文 | 1 |
| 现代 | 折页 | folio | 中文 | 1 |
| 现代 | 环线 | orbit | 中文 | 2 |
| 行业 | 产品·航标 | product | 中文 | 1 |
| 行业 | 增长·刻度 | growth | 中文 | 2 |
| 行业 | 诊疗·青禾 | clinical | 中文 | 1 |
| 行业 | 顾问 Consultant | consultant | 英文 | 1 |

每套具有独立 manifest、Layout.vue 和注册入口，复用 ResumeDocument、RRegion、现有内容块与分页引擎。每套提供 3 种配色和标准／紧凑页头，兼容既有字体、字号和间距设置。中文使用 A4、照片可选；英文支持 A4／Letter、不显示照片。双栏按主栏后侧栏的顺序输出文字，续页使用紧凑页眉。页数限制为 2 页，超出目标容量时沿用现有溢出报告，保留完整内容供调整。

模板中心、详情页、工作台和首页共用模板注册表与清单；后端生成清单同步更新，原模板 ID 保持不变。缩略图脚本读取清单并按语言选用示例，已重生成 32 张首页缩略图。未新增业务 API、数据库字段或迁移。

打印准备阶段新增对可见图片和装饰背景的解码等待，素材加载失败会返回错误，不会以缺图状态静默完成打印。手机模板卡片增加标题换行与配色按钮布局约束，避免长名称被挤成逐字排列。

## 素材与生成记录

已通过提供的图片服务核验模型能力并使用 `gpt-image-2.5-flare` 生成候选。正式素材仅包含装饰，文字全部由页面渲染。几何边框、星图和线条由 CSS／SVG 构成。

| 正式素材 | 选中候选 | 格式 | 文件字节数 |
| --- | --- | --- | ---: |
| botanical-sprig | botanical-sprig-v1.png | 透明 WebP | 159,026 |
| paper-cotton | paper-cotton-v3.png | JPEG | 40,735 |
| woven-strip | woven-strip-v1.png | JPEG | 126,042 |
| paper-cut | paper-cut-v2.png | 透明 WebP | 94,092 |
| shoreline-waves | shoreline-waves-v2.png | 透明 WebP | 73,136 |
| porcelain-branch | porcelain-branch-v2.png | 透明 WebP | 170,546 |

六份正式素材合计 663,577 字节，随前端本地打包。有纹理／插画的 8 套设计感模板提供装饰关闭选项。棉纸初版与第二版带有边框，已弃用并重生成无边框的第三版。透明原稿的透明像素比例约 84%–89%，导出保留 Alpha。

提示词、生成参数、棉纸重试提示词、导出脚本与质量数据保存在 `design/template-assets/collection-2026/`。原始 PNG 保留于该目录的本地忽略子目录 `originals/`。API 凭据通过开发进程环境传入，生成记录与交付包不含凭据。

## 自动化检查

| 检查 | 结果与覆盖范围 |
| --- | --- |
| 前端逻辑测试 | 229 项通过 |
| 前端组件测试 | 63 个测试文件、310 项通过；含清单／布局加载及图片等待测试 |
| 设计规则与清单检查 | 0 个设计规则问题；32 套前后端清单与渲染示例一致 |
| 最终前端构建 | `vue-tsc -b` 与生产构建通过，包含最后的手机卡片样式修正 |
| 后端相关测试 | 75 项通过，0 失败、0 错误、0 跳过；覆盖模板同步幂等、目录、工作台、设计设置与换版保存 |
| 新模板集成测试 | 遍历新增 16 套，创建会话、应用模板、保存配色及紧凑页头、切至 classic 再切回；设置恢复且正文不变 |
| 真实渲染契约 | 全部 32 套通过；共 122 次实际 PDF 渲染请求，验证文字层、栏目／条目顺序、字体嵌入、页数与溢出 |
| 渲染服务测试 | 6 项通过 |
| 浏览器排版矩阵 | 新增模板 206 个场景通过；包含默认／另两种配色、紧凑页头、短／长内容、长联系方式、中英文混排、替代字体、大字号／宽边距、照片开关、隐藏／重排栏目、双栏重新分配、关闭装饰及 Letter |
| PDF 文件检查 | 16 份、22 页；必需文字、字体嵌入、页面边界和链接检查通过，无缺失条目或越界文字 |
| 预览与 PDF 对照 | PDF 栅格化后逐页与浏览器预览对照，最大平均绝对像素差为 3.227／255 |
| 目录页面 | 1440px 和 390px 检查通过；分类数量正确，设计感目录无横向溢出、卡片标题越界、破图或浏览器异常；植笺详情页正常 |
| 本地实际 PDF 服务 | 3100 端口服务刷新后，新增 16 套全部成功返回 PDF |
| 后端打包 | Maven package 通过；本地后端已重新启动 |

16 份默认 PDF 合计 5,052,848 字节，最大单份为 920,935 字节。PDF 使用共享中文 professional／英文 english 示例以便比较版式；行业示例沿用现有简历字段，不补造专属业务字段。

后端本次运行的是相关 75 项测试及单独的真实渲染契约检查，并非整个后端测试集。集成测试使用测试数据库；目录与实际渲染服务检查使用本地运行环境。

## 截图视觉复核

本节为执行代理对已生成截图的视觉查看，与程序断言分开记录；未经过独立人员人工终审。

| 查看材料 | 观察结果 |
| --- | --- |
| 16 套默认预览总览、PDF 总览 | 姓名层级、栏目结构和边缘装饰可区分；未观察到明显遮挡或被裁掉的文字 |
| 紧凑页头、照片、替代配色总览 | 页头收拢正常，照片与联系区分开，配色保持正文对比度 |
| 关闭装饰的 8 套设计感总览 | 图像装饰消失，正文与结构仍完整 |
| 默认示例的续页截图与新增 16 套长内容第二页总览 | 页眉紧凑，内容延续与页码正常；双栏续页保持主栏和侧栏的结构 |
| 手机模板目录截图 | 修复长名称拥挤后，标题与配色按钮排列正常 |
| 8 套设计感模板的装饰 200% 局部 | 逐套查看植物、拱线、织物、拼贴、海浪、瓷纹、星图与棉纸区域；细线与浅色纹理可辨识，未观察到明显压缩块或破图 |

上述视觉复核覆盖默认图、变体汇总图、16 套第二页与 8 套装饰放大图；自动化矩阵的 206 个场景并非每个截图都由独立人员人工审阅。第二页总览使用加长内容验证分页衔接，超出两页时仍显示溢出信息，不将该压力样例当作两页成品。

## 文件与复现

本地应用入口：`http://127.0.0.1:5174/resume-templates`。

实施文件：

- 模板：`frontend/src/resume-render/templates/{模板 ID}/`，共享排版样式为 `collection.css`。
- 正式素材：`frontend/src/resume-render/assets/art/`；来源与导出记录：`design/template-assets/collection-2026/`。
- 打印等待：`frontend/src/print/imagesReady.ts`、`PrintApp.vue`。
- 清单：`frontend/src/resume-render/templates/manifests.ts`、`registry.ts`、`backend/src/main/resources/templates/builtin-manifests.json`。
- 验收脚本：`frontend/scripts/template-collection-acceptance.mjs`、`template-gallery-acceptance.mjs`、`scripts/verify-template-collection.py`。

完整本地证据位于 `.local-run/template-collection-20261021/`，包含逐套 PDF、逐页 PNG、变体截图、目录截图与 JSON 结果。交付包为 `.local-run/template-collection-20261021-delivery.zip`，包含 16 份 PDF、预览／PDF 总览、验收报告、结果数据、正式素材和提示词记录。

主要日志位于 `.local-run/templates-frontend-test.log`、`templates-backend-test.log`、`templates-renderer-contract.log`、`templates-renderer-test.log`、`templates-matrix.log`、`templates-gallery.log`、`templates-final-build.log`。

PowerShell 复现命令（使用 Node 22.18+；需启动本地前后端与渲染服务）：

```powershell
Set-Location frontend
npm test
npm run build
$env:CHROMIUM_PATH = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
node scripts/template-collection-acceptance.mjs
node scripts/template-gallery-acceptance.mjs
node scripts/render-template-thumbnails.mjs http://127.0.0.1:5174
Set-Location ..
python scripts/verify-template-collection.py
mvn -B -f backend/pom.xml '-Dtest=BuiltInTemplateSynchronizerIT,ResumeTemplateCatalogIT,ResumeTemplateCenterIT,SmartTemplateCollectionIT,AiResumeWorkbenchIT,ResumeDesignV2Test' test
# JP_RENDERER_URL 与 JP_RENDERER_TOKEN 使用当前渲染服务环境配置，不写入文件。
mvn -B -f backend/pom.xml '-Dtest=RendererContractTest' test
Set-Location renderer
npm test
```

PDF 复核脚本需要 PyMuPDF、Pillow、NumPy。目录脚本在需要登录的环境可读取 `JOBPROOF_TEST_ACCOUNT`／`JOBPROOF_TEST_PASSWORD` 进程环境变量。检查脚本默认使用本次本地证据目录，也支持 `JOBPROOF_TEMPLATE_OUTPUT` 或 `--input` 指定输出目录。

此次仅更改模板、素材、缩略图、打印素材等待、手机模板目录布局及对应验证文件；已有其他工作区修改予以保留。临时 3101 验收渲染服务已停止，正常 3100 PDF 服务保留运行。
