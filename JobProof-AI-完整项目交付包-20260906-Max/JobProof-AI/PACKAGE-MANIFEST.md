# 交付内容清单

交付日期：2026-09-06

## 已包含

- `frontend/`：Vue/TypeScript 源码、锁文件、单元/组件/E2E 测试和构建配置。
- `backend/`：Java/Spring Boot 源码、Flyway 迁移、测试、Dockerfile、Maven 配置及已验证可运行 JAR。
- `deploy/`：HTTP/HTTPS Compose、Nginx 配置、环境变量模板、备份与恢复演练脚本，以及由当前前端源码构建的 `web/`。
- `scripts/`：本地启动/停止、部署准备、生产配置预检、安全扫描和许可证检查工具。
- `.github/`：现有自动化配置。
- `开发文档/`、`docs/`、`ai对话功能全量开发/`：现行和历史开发资料。
- `JobProof-AI-工作台界面图-v1.0/`：工作台界面图与跳转说明。
- `JobProof_AI_全栏目SVG图标库_v1/`：SVG 图标库和接入说明。
- `简历模板/`：2160 个源文件，共 901586366 字节；其中 2132 个为 `.docx` 模板。
- `MANIFEST-SHA256.txt`：压缩前生成的逐文件 SHA-256，可用于检查解压内容完整性。

## 明确排除

- 原工作区中的借鉴/参考项目，它们不是本产品源码。
- Git 元数据、编辑器配置、临时目录和 Codex 工作目录。
- `node_modules/`、旧 `dist/`、Maven 中间产物、测试报告、缓存和日志。
- H2/SQLite 数据库、本地用户文件、部署数据、Docker 卷、备份和证书。
- 真实 `deploy/.env` 和任何真实 SMTP、短信、AI、对象存储或云厂商密钥。
- 模板库的重复 ZIP；已保留完整解压内容。
- 多轮构建遗留的旧 `deploy/web/` 和意外嵌套的重复后端目录。

## 可再生内容

`frontend/node_modules/` 由 `npm ci` 根据 `package-lock.json` 生成。`frontend/dist/`、`deploy/web/` 和后端 JAR 由 `scripts/prepare-deployment.ps1` 或 `scripts/prepare-deployment.sh` 重新生成。运行时数据不应打入源码交付包。

## 完整性核验

Windows PowerShell：

```powershell
$root = (Get-Location).Path
Get-Content MANIFEST-SHA256.txt | ForEach-Object {
  $hash, $path = $_ -split '  ', 2
  if ((Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $root $path)).Hash.ToLowerInvariant() -ne $hash) {
    throw "校验失败: $path"
  }
}
```

Linux：

```sh
sha256sum -c MANIFEST-SHA256.txt
```

`MANIFEST-SHA256.txt` 不列出它自身，以避免自引用哈希。
