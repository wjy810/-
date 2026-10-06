# JobProof AI 完整项目交付包

本目录是可直接交接的项目副本，包含当前前后端源码、数据库迁移、测试、部署编排、开发文档、界面设计资料、SVG 图标库和完整 Word 简历模板库。真实业务数据、本机缓存、日志、私钥和生产 `.env` 不在交付包中。

## 首次使用

要求 JDK 17+、Node.js `>=24.15.0 <25` 和 npm 11.x；修改源码或重新构建还需 Maven 3.8+。本交付验证使用 Node.js 24.19.0。不要使用 Python 启动本项目。

Windows PowerShell：

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\start-local.ps1
```

Linux/macOS：

```sh
chmod +x scripts/*.sh
./scripts/start-local.sh
```

首次启动会在缺少依赖时执行 `npm ci`，并在缺少后端 JAR 时执行 Maven 验证。启动成功后访问 <http://127.0.0.1:5174/>；停止命令为 `.\scripts\stop-local.ps1` 或 `./scripts/stop-local.sh`。

修改源码后用 `.\scripts\start-local.ps1 -Rebuild` 或 `./scripts/start-local.sh --rebuild` 强制重建；发布前始终运行 `prepare-deployment` 脚本。

## 本地开发账号

| 角色 | 登录名 | 密码 | 邮箱 |
| --- | --- | --- | --- |
| 管理员 | `admin` | `admin` | `admin@jobproof.local` |
| 求职者 | `seeker` | `seeker123` | `seeker@jobproof.local` |

这些固定凭据只在默认 `dev` 配置中启用，仅供本地开发，禁止用于公网或生产环境。生产管理员没有通用默认密码，必须按交接指南执行一次性初始化并立即移除初始化密码。

## 文档入口

- [项目交接、运行与部署指南](docs/项目交接与运行部署指南.md)：接手者从零启动、生产部署、管理员初始化、备份、升级和回滚的权威说明。
- [交付内容清单](PACKAGE-MANIFEST.md)：目录用途、纳入和排除内容、验证边界。
- [交付验证报告](docs/交付验证报告.md)：本次交付副本的构建、测试、登录和浏览器验收结果。
- [后端说明](backend/README.md) 与 [前端说明](frontend/README.md)：模块和开发细节。
- `开发文档/`：PRD、架构、技术选型和业务契约。
- `docs/`：当前实施记录、安全、容量、HTTPS、备份恢复和验收资料；其中 `docs/plan/`、`docs/audits/` 是历史过程记录，不覆盖本页和交接指南。

生产部署不要直接复用本地账号或示例密钥。完整流程见 [项目交接、运行与部署指南](docs/项目交接与运行部署指南.md)。
