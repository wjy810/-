# 容器镜像安全门禁

## 2026-09-05 本地复验

使用 Trivy 0.69.3 的同一漏洞数据库扫描 Compose 镜像；报告保留在 `.codex_tmp/ops-security-current/`。镜像标签通过 Docker Hub 官方仓库 API 核对，Compose 中可升级镜像同时固定多架构 manifest digest。

| 镜像 | 结果 | 处理 |
| --- | --- | --- |
| `redis:7.4-alpine` | 0 | 保持现状 |
| `eclipse-temurin:17-jre-jammy` | 0 Critical/High；81 Medium、32 Low | 保持 Java 17 运行基线，持续扫描 |
| `nginx:1.28-alpine` | 2 Critical、50 High | 升级为 `nginx:1.30.4-alpine-slim`；候选复扫为 0 |
| `clamav/clamav:1.4.6` | 本机旧 digest 有 2 High | 官方 2026-08-31 已重新构建同标签；1.5.4 候选复扫没有 Critical/High/Medium/Low、剩 10 个 UNKNOWN，但本机隔离启动时 Docker Engine API 失去响应，运行时升级未验收，因此 Compose 暂不切换 |
| `minio/minio:RELEASE.2025-09-07T16-13-09Z` | 8 Critical、96 High | 官方仓库截至复验时没有比当前 2025-09-07 更新的社区标签；不能通过安全换标签修复 |
| `certbot/certbot:v5.8.0` | 8 High | 官方稳定最新仍为 5.8.0；仅在签发/续签时按需运行，但上线前仍需处置或明确风险接受 |

Nginx 候选确认包含 `/docker-entrypoint.sh`、`nginx` 和 `wget`，现有配置及健康检查可用。ClamAV 1.5.4 候选确认保留 `/init`、`/usr/local/bin/clamdcheck.sh`、`/var/lib/clamav` 和 `CLAMAV_NO_FRESHCLAMD` 支持，但静态兼容不能替代健康启动与 INSTREAM 端到端；该升级未被接受。

## 2026-09-06 续验

ClamAV 保持 `1.4.6`，固定官方重新构建的 manifest digest `sha256:b25d9199257ae7ef45e0cc4c7eaa60ce7e5447796f0090d5ff311d1a30980cc3`。Trivy 0.69.3 使用 2026-09-05 19:14 UTC 更新的数据库重新扫描，结果为 **0 Critical、0 High、0 Medium、0 Low、10 UNKNOWN**。旧镜像 `libcrypto3` / `libssl3` 的 `CVE-2026-14456` 已消失；UNKNOWN 均来自 `libcurl 8.21.0-r0`，数据库列出 `8.22.0-r0` 修复版本，不能把未定级解释为无风险。扫描报告位于 `.codex_tmp/ops-security-final-20260906/clamav-1.4.6-rebuilt.json`。

隔离容器 `jobproof-clamav146-validation-20260906` 使用全新专用病毒库卷、1400 MiB 上限，没有发布宿主端口。官方启动流程及 `clamdcheck.sh` 均通过；实际 INSTREAM 协议返回干净样本 `stream: OK`，标准 EICAR 测试签名 `stream: Eicar-Test-Signature FOUND`。引擎返回 `ClamAV 1.4.6/28108/Sun Aug 30 06:27:10 2026`。复验脚本为 `deploy/tests/clamav-instream-check.py`，只在网络流中提交测试签名，不落盘恶意样本；证据摘要为 `.codex_tmp/ops-security-final-20260906/runtime-verification.json`。

Compose 和 CI 的 ClamAV/Nginx 使用同一固定 digest；CI 补入 HTTPS 续签所需 Certbot 镜像。MinIO、Certbot 原有高危项仍需处置，ClamAV 的 10 个未定级项仍需追踪。应用 JAR 的独立扫描结果由应用安全报告负责，不能把旧 JAR 结果套用到新包。

### Certbot 高危项定向复核

2026-09-06 05:20 CST 核对 Docker Hub：最新稳定仍为 `v5.8.0`，manifest digest 为 `sha256:f70ad0adbb7e117f0fe42a63c553f28ea451edabc0148757b6efcd9735acaa20`。2026-09-05 更新的 nightly digest `sha256:cb356ae8e39e5dd0314a36dde4a2f3f28ed440095efca315b55096a2a16680b0` 已通过远程镜像扫描复核，仍为 **8 High、1 Medium**，没有提供安全修复收益，未切换部署。

| 来源 | 当前版本 | 修复证据 | 结论 |
| --- | --- | --- | --- |
| Alpine `libuuid` | `2.41.4-r0` | 6 条 High 均指向 `2.41.6-r0`；官方 v3.23 x86_64 仓库已有 `libuuid-2.41.6-r1.apk` | 可验证的同发行版补丁候选，需重建与重扫 |
| pip 内置 `msgpack` | `1.1.2` | `GHSA-6v7p-g79w-8964`，修复版 `1.2.1` | 属于 pip vendored 副本，安装顶层同名包不会替换它 |
| pip 内置 `setuptools` | `70.3.0` | High `CVE-2025-47273` 修复版 `78.1.1`；另有 Medium 指向 `83.0.0` | 实际顶层 setuptools 已为 `83.0.0`，继续升级顶层包不能修复内置副本 |

证据来自官方 Python 基础层 `sha256:bfac9e75463e202fb5625297d2efe2f15f7f6fc2bbf60af60cd2d5c4d4e97046`：`usr/local/lib/python3.14/site-packages/pip/_vendor/vendor.txt` 明确列出 `msgpack==1.1.2` 与 `setuptools==70.3.0`；msgpack 模块也声明 `__version__ = "1.1.2"`。`ensurepip/_bundled/pip-26.2.1-py3-none-any.whl` 仍保留在 Python 标准库。PyPI 最新稳定 pip 为 `26.2.1`，因此简单执行 `pip install --upgrade pip` 目前也不能解决。不能仅通过删除 SBOM 或关闭扫描器隐藏这些记录。

最小候选方案是保持 Certbot `5.8.0` 应用版本与官方固定基底，升级 Alpine `libuuid`，并在专用于 webroot 签发/续签的最终运行镜像中评估移除构建用 pip 及 ensurepip 内置 wheel。官方 `tools/docker/Dockerfile` 使用 `tools/pip_install.py` 在构建期间安装应用；PyPI Certbot/ACME 的基础运行依赖没有要求 pip。这个候选仍需验证 CLI、webroot 插件、现有账户/续签配置兼容及隔离 ACME 签发/续签，再对最终镜像重新扫描。动态安装插件的维护方式会受影响，不能在未验证时直接替换。

本次是只读复核，未建立或发布衍生镜像。Docker/WSL 的单次 15 秒状态检查仍超时，阻止运行时验证；没有重启或停止其他项目。证据保留于 `.codex_tmp/ops-security-final-20260906/certbot-nightly-evaluation.json`、`certbot-python-base-layer.tar.gz` 和 `certbot-remediation-review.json`。官方资料：[镜像标签](https://hub.docker.com/v2/repositories/certbot/certbot/tags?page_size=12&ordering=last_updated)、[Certbot Dockerfile](https://github.com/certbot/certbot/blob/v5.8.0/tools/docker/Dockerfile)、[Alpine v3.23 包目录](https://dl-cdn.alpinelinux.org/alpine/v3.23/main/x86_64/)、[pip 元数据](https://pypi.org/pypi/pip/json)。

## 发布规则

正式构建必须重新扫描实际解析到的镜像 digest，而不是只扫描标签文字。任何 Critical/High 默认阻断发布；UNKNOWN 必须记录来源与人工结论。当前 MinIO、Certbot 的 Critical/High 尚未清零，因此整体发布门禁仍不通过；应用 JAR 另以最终包的独立重扫为准。不得通过忽略文件或调低严重性来伪造通过。

MinIO 的后续选择需要产品与架构决策：使用有安全维护的兼容发行版、迁移到受控 S3/OSS 服务，或完成替代对象存储适配与数据迁移。Certbot 可等待包含修复依赖的稳定镜像，或在独立、最小权限的签发主机运行经扫描的发行版。上述改变都不是本轮可无风险自动替换的标签升级。
