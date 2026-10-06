# 后端依赖安全收尾记录

验收日期：2026-09-06。项目根目录：本交付包根目录。

最终 JAR 已独立完成漏洞、生产源码凭据、包内凭据及许可声明核验。Trivy 实际识别 148 个 Java 包，Critical、High、Medium、Low、Unknown 均为 0。该结论对应下面的精确构建产物和漏洞库日期，不等同于容器镜像、线上配置或全部安全问题已消除。

## 最终产物与证据

- 产物：`backend/target/jobproof-backend.jar`。
- SHA-256：`D46F73834A3184314A1E12E2CB6B8B0C3F343D7B21EDEB4EAAC214AA6472E86B`。
- 冻结副本：`.codex_tmp/security-final-20260906/jar-input/jobproof-backend.jar`。
- 扫描完成时间：`2026-09-06T05:13:22+08:00`。
- 扫描器：Trivy `0.69.3`；下载压缩包 SHA-256 与对应发布校验文件一致。
- 漏洞库更新时间：`2026-09-05T19:14:02Z`；本次重新下载于 `2026-09-05T20:29:24Z`。
- Java 索引库更新时间：`2026-09-05T01:05:40Z`；本次重新下载于 `2026-09-05T20:30:53Z`。

证据均位于 `.codex_tmp/security-final-20260906/`：

| 文件 | 内容 |
| --- | --- |
| `scan-metadata.json` | 产物与扫描器哈希、完成时间、退出码、148 包覆盖数和数据库版本。 |
| `jar-vulnerabilities.json` | 最终完整 Trivy JSON，包含识别到的包与版本。 |
| `jar-summary.json` | 最终 C/H/M/L/Unknown 计数，均为 0。 |
| `source-credential-scan.txt` | 553 个生产源码或配置条目，凭据规则命中 0。 |
| `jar-credential-scan.txt` | 985 个 `BOOT-INF/classes` 条目，凭据规则命中 0。 |
| `java-licenses.json` | 137 个嵌入依赖 JAR 的包内 POM 原始许可声明。 |
| `java-license-declarations-resolved.json` | Maven Central 对应版本及父 POM 补充声明、来源 URL、POM SHA-256。 |
| `release-verification.json` | 第二轮 8 组修复版本的真实 POM GET 检查，HTTP 200 且声明版本吻合。 |
| `baseline-03599/` | 第一轮产物及完整证据，未被第二轮覆盖。 |
| `baseline-F6C3/` | 依赖升级后首个零漏洞产物，保留供最终业务修复包做依赖字节对照。 |

## 中断前后的修复

| 阶段 | Critical | High | Medium | Low | 证据 |
| --- | ---: | ---: | ---: | ---: | --- |
| 旧依赖线历史扫描 | 8 | 29 | 29 | 11 | `.codex_tmp/ops-security-current/backend-clean-jar.json`。仅作为历史比较。 |
| 第一轮升级，JAR `03599AE8...D9BE65` | 0 | 0 | 10 | 2 | `baseline-03599/jar-summary.json`。本次实际解析 148 包。 |
| 第二轮依赖修复 JAR `F6C3B36B...80642CC` | 0 | 0 | 0 | 0 | `baseline-F6C3/jar-summary.json`。实际解析 148 包。 |
| 内部错误分派修复后的最终 JAR `D46F7383...6472E86B` | 0 | 0 | 0 | 0 | `jar-summary.json`。重新扫描，实际解析 148 包。 |

第一轮已升级 Spring Boot、Spring Framework、Spring Data、Spring Security、Tomcat、Jackson、Netty、Micrometer、HttpCore、MinIO SDK 和 Bouncy Castle，消除了历史扫描中的全部 Critical/High 命中。第二轮继续修复真实可获取版本能够解决的 10 项 Medium 和 2 项 Low，没有添加忽略规则来隐藏结果。

最终版本依据 `backend/pom.xml:10`、`backend/pom.xml:25`、`backend/pom.xml:93` 以及最终 JAR 的 `jar-vulnerabilities.json` 核实：

| 依赖 | 最终版本 | 处理方式 |
| --- | --- | --- |
| Spring Boot | 3.5.15 | 升级父 POM。 |
| Spring Security | 6.5.11 | 使用 Boot 3.5.15 BOM。 |
| Logback | 1.5.34 | 使用 Boot 3.5.15 BOM。 |
| Jackson | 2.21.5 | BOM 版本属性覆盖。 |
| Commons Lang | 3.18.0 | 版本属性覆盖。 |
| Apache HttpClient | 5.6.3 | 版本属性覆盖；该版本上游匹配 HttpCore 5.4.3。 |
| Log4j API/桥接 | 2.25.5 | BOM 版本属性覆盖。 |
| jsoup | 1.23.1 | 显式依赖版本更新。 |
| Tomcat | 10.1.59 | 保留已修复版本覆盖。 |
| Spring Framework | 6.2.19 | 保留已修复版本覆盖。 |
| Spring Data Commons | 3.5.12 | Spring Data BOM 2025.0.12。 |
| Netty | 4.1.136.Final | 保留已修复版本覆盖。 |
| Micrometer | 1.15.12 | 保留已修复版本覆盖。 |
| HttpCore | 5.4.3 | 保留已修复版本覆盖。 |
| MinIO Java SDK | 8.6.0 | 保留显式安全版本。 |
| Bouncy Castle | 1.84 | 保留显式安全版本。 |

第一轮剩余 12 项均来自 Trivy 的 `GitHub Security Advisory Maven` 数据源，严重性来源也是 GHSA。对应公告为：Jackson 的 `CVE-2026-54515`、`CVE-2026-59889`、`GHSA-mhm7-754m-9p8w`；Commons Lang 的 `CVE-2025-48924`；HttpClient 的 `CVE-2026-64607`；Log4j 的 `CVE-2026-49844`；jsoup 的 `CVE-2026-71497`；Boot 的 `CVE-2026-41001`；Security 的 `CVE-2026-41706`、`CVE-2026-47838`；Logback 的 `CVE-2026-10532`、`CVE-2026-9828`。完整公告、来源和修复版本保存在 `baseline-03599/jar-vulnerabilities.json`，最终包已不再命中。

## 实际调用与扫描范围

HttpClient 的 classic I/O 调用实际存在于 `backend/src/main/java/com/jobproof/modules/jobmatch/application/JobMatchTextService.java:185` 和 `:189`；恶意响应的 `Content-Encoding` 属于实际外部输入，升级对该链路有直接价值。现有每次调用的 `try-with-resources` 会关闭客户端，降低连接泄漏持续积累风险，但不作为保留旧版本的理由。

jsoup 当前在同一文件 `:212` 仅将抓取 HTML 转为纯文本。检索生产源码未发现自定义 `Safelist`、公告要求的 Jackson `JsonView`/`JsonUnwrapped` 组合、CookieRequestCache、X.509 认证、Artemis 或 Logback SocketServer 配置。这里只能得出未发现对应直接配置，不能由静态检索证明所有间接调用不可达；因此仍统一升级已发布修复版本。

凭据扫描采用 `scripts/security-source-scan.py:10` 的有限规则，并覆盖 `backend/src/main`、前端源码与脚本、部署脚本和 CI 定义；包内扫描覆盖 `BOOT-INF/classes`，也检查被禁止的内嵌开发凭据类。扫描不输出命中值。`scripts/security-source-scan.py:67` 与 `:73` 定义的范围不含运行时 `.env`、历史产物、测试夹具和运行数据，零命中不能证明曾使用的云凭据已经轮换。

最初使用 `trivy fs` 时没有启用此运行时的 JAR 分析器，返回 `num=0`；该结果已判为无效。正式结果使用 `trivy rootfs`，完整分析嵌套依赖，并在 `.codex_tmp/security-final-20260906/scan-final.ps1` 中增加 Java 包数必须大于 0 的断言。现有 CI `.github/workflows/verify.yml:62` 已使用 `rootfs`，`scripts/check-vulnerability-report.py:14` 也拒绝空扫描结果。

## 许可台账

原始脚本 `scripts/java-license-inventory.py:16` 按 137 个嵌入 JAR 查找包内 POM 直接声明，其中 41 个有直接声明、96 个需要进一步查父 POM 或发布资料。这 96 个不是 96 个违规项。

补充核验以最终 Trivy 识别的 148 个组件记录为起点，逐个读取 Maven Central 相同版本 POM 并追踪父 POM。147 个第三方组件记录均找到了许可声明，未解析的第三方项为 0；剩余 1 项是项目自身 `com.jobproof:jobproof-backend`，不适用公共 Maven 许可查询。组件记录数包含聚合 JAR 内的多个 Maven 组件，因此与嵌入 JAR 文件数不同。

JaCoCo 内部 `org.jacoco.agent.rt` 的声明通过发布的 `org.jacoco.agent:0.8.4:runtime` 关联；嵌入文件 SHA-1 与 Maven Central 的相同 classifier 校验文件一致，关联证据已记录。每个声明保留名称、URL、父 POM 链和 POM SHA-256，不自行把多重声明解释成 AND 或 OR。

最终 `D46F7383...6472E86B` 包与已完成声明核验的 `F6C3B36B...80642CC` 包，其 137 个 `BOOT-INF/lib` JAR 的 SHA-256 逐文件完全一致。补充许可台账保留声明最初核验时间，并记录新产物哈希、再次核验时间和完整依赖哈希清单；没有将旧包结果直接冒充新包核验。

许可声明台账已经补全，许可兼容性和分发义务仍取决于产品分发方式。台账明确包含 MySQL Connector/J 的 GPLv2 + Universal FOSS Exception、Hibernate 的 LGPL、Logback 的 EPL/LGPL 以及 Jakarta 部分多重许可。发布 JAR、私有化交付或分发修改版依赖前，应按具体采用的许可及例外完成通知和分发义务核对。当前结果没有将这些声明自动归为违规，也没有声称完成法律审批。

## 复现

在项目根目录运行：

```powershell
& '.codex_tmp/security-final-20260906/scan-final.ps1' -ExpectedHash 'D46F73834A3184314A1E12E2CB6B8B0C3F343D7B21EDEB4EAAC214AA6472E86B'
& 'D:/python/python.exe' 'scripts/check-vulnerability-report.py' '.codex_tmp/security-final-20260906/jar-vulnerabilities.json'
& 'D:/python/python.exe' '.codex_tmp/security-final-20260906/resolve-license-declarations.py'
```

复现脚本冻结并核对 JAR 哈希；漏洞扫描使用上述已下载数据库。未来发布需要重新更新漏洞数据库后扫描新产物。容器镜像问题与许可证参见 `docs/container-security.md`；线上 HTTPS、真实凭据轮换、外部供应商验收和部署状态由主实施记录单独跟踪。
