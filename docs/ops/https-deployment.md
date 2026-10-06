# HTTPS 发布脚手架（尚未上线）

本配置支持域名及公网 IP 的 HTTP-01 验证；没有运行签发、修改防火墙或连接线上服务器。生产启用必须由管理员在维护窗口执行。IP 证书已由 Let's Encrypt 开放，必须使用 `shortlived` 配置；其有效期约六天，因此每天至少运行两次续签并对失败及剩余有效期报警。

## 前置条件与首次签发

确认 80/443 入站可达、ACME HTTPS 出站可达、公网 IP 属于本机（域名场景 DNS 指向本机），并接受 CA 服务条款。备份现有配置，保存回滚版本；不要把私钥提交到仓库。检查 `172.31.247.0/24` 与现有路由/网络不冲突；若修改，必须同步覆盖 Compose 中的地址和 `JOBPROOF_TRUSTED_PROXIES`。应用只信任 Nginx `172.31.247.3/32`，Nginx 覆盖客户端自带的 XFF；不要在前方任意加代理后直接复用该信任模型。

在 `deploy` 目录准备 `acme-webroot`、`letsencrypt`、`acme-logs`（权限受控）；首先保留 HTTP 的基础 Compose 配置，仅应用新增 webroot 挂载，检查 `/.well-known/acme-challenge/` 测试文件可从公网访问。

以下示例使用保留示例 IP/域名，执行前替换。先加 `--dry-run` 用 staging 演练，成功后去掉该参数正式签发。Certbot 固定为 5.8.0，支持 `--ip-address`。

```sh
export JOBPROOF_HTTPS_ORIGIN=https://203.0.113.10
docker compose -f docker-compose.yml -f docker-compose.https.yml run --rm --no-deps certbot certonly --webroot -w /var/www/certbot --cert-name jobproof --ip-address 203.0.113.10 --required-profile shortlived --email admin@example.com --agree-tos --non-interactive --dry-run
# 域名场景：将 --ip-address 203.0.113.10 换为 -d jobs.example.com；同步修改 origin。
# 不要在首次证书产生之前启动 HTTPS Nginx，它会因找不到证书拒绝启动。
```

正式证书存在后运行组合 Compose 的 `config --quiet`，然后按现有发布流程启用 `docker-compose.https.yml`。它会保留 80、增加 443，并设置 Secure cookie、HTTPS origin 和唯一可信代理。先验证 `nginx -t` 再平滑 reload。验收 HTTP→HTTPS、证书 SAN/有效期/可信链、登录 Cookie Secure、登录/注销/跨域/API/SSE、HTTPS 麦克风授权、伪造 XFF 不改变限流身份。应用/Redis/MinIO 不得发布到宿主公网端口。

## 续签、告警、回滚

将 `JOBPROOF_HTTPS_ORIGIN` 放入受控服务环境，以操作系统定时器每日两次运行 `sh deploy/renew-certificates.sh`；首次手动运行带 `--dry-run` 并保留退出码证据。脚本在 Certbot 成功且 `nginx -t` 通过后 reload，失败非零退出，不自动恢复 HTTP 或禁用 Secure cookie。调度器和报警本次未安装。

证书目录与账号密钥应单独加密备份；它们不属于 `backup.sh` 的应用数据归档。失效时优先修复续签或恢复仍有效证书，不建议降级登录到 HTTP。需要回滚发布时恢复原镜像和原配置并保持 TLS；仅在明确批准的维护方案中恢复 HTTP。

官方依据：[Let's Encrypt IP/短期证书公告](https://letsencrypt.org/2026/01/15/6day-and-ip-general-availability.html)、[Certbot 命令及续签说明](https://eff-certbot.readthedocs.io/en/stable/using.html)。
