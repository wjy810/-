[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$deployDir = [IO.Path]::GetFullPath((Join-Path $projectRoot 'deploy'))
$envFile = [IO.Path]::GetFullPath((Join-Path $deployDir '.env'))
if ([IO.Directory]::GetParent($envFile).FullName -ne $deployDir) { throw 'deploy/.env 路径校验失败。' }
if (-not (Test-Path $envFile)) { throw '缺少 deploy/.env，请先从 .env.example 复制并填写。' }

$content = Get-Content -Raw -LiteralPath $envFile
if ($content -match '(?i)replace-with|your-server-ip-or-domain') {
    throw 'deploy/.env 仍包含占位符，拒绝部署。'
}

$required = @(
    'JOBPROOF_PUBLIC_ORIGIN',
    'JOBPROOF_REDIS_PASSWORD',
    'JOBPROOF_MINIO_ACCESS_KEY',
    'JOBPROOF_MINIO_SECRET_KEY',
    'JOBPROOF_AI_MASTER_KEY',
    'JOBPROOF_CONTACT_HMAC_SECRET',
    'JOBPROOF_EMAIL_CODE_HMAC_SECRET'
)
foreach ($key in $required) {
    $match = [regex]::Match($content, "(?m)^$([regex]::Escape($key))=(.+)$")
    if (-not $match.Success -or [string]::IsNullOrWhiteSpace($match.Groups[1].Value)) {
        throw "deploy/.env 缺少非空配置: $key"
    }
}

$masterKey = [regex]::Match($content, '(?m)^JOBPROOF_AI_MASTER_KEY=(.+)$').Groups[1].Value.Trim().Trim('"')
try { $masterKeyBytes = [Convert]::FromBase64String($masterKey) }
catch { throw 'JOBPROOF_AI_MASTER_KEY 不是有效 Base64。' }
if ($masterKeyBytes.Length -ne 32) { throw 'JOBPROOF_AI_MASTER_KEY 解码后必须恰好为 32 字节。' }

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw '缺少 Docker。' }
Push-Location $deployDir
try {
    & docker compose config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose 配置校验失败。' }
} finally { Pop-Location }

if ($content -match '(?m)^JOBPROOF_DEV_ADMIN_ENABLED=true\s*$') {
    Write-Warning '管理员一次性初始化器处于启用状态；初始化完成后必须关闭并清空密码。'
}
Write-Host 'deploy/.env 必填项、占位符、AI 主密钥和 Compose 语法检查通过。'
