[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$frontendDir = Join-Path $projectRoot 'frontend'
$backendDir = Join-Path $projectRoot 'backend'
$deployDir = [IO.Path]::GetFullPath((Join-Path $projectRoot 'deploy'))
$distDir = [IO.Path]::GetFullPath((Join-Path $frontendDir 'dist'))
$webDir = [IO.Path]::GetFullPath((Join-Path $deployDir 'web'))

foreach ($command in @('node', 'npm.cmd', 'java', 'mvn')) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "缺少命令 '$command'。"
    }
}

$nodeVersion = & node -p "process.versions.node"
if ([version]$nodeVersion -lt [version]'24.15.0' -or [version]$nodeVersion -ge [version]'25.0.0') {
    throw "需要 Node.js >=24.15.0 且 <25，当前为 $nodeVersion。"
}
$nodeExe = (Get-Command 'node').Source
$npmCommand = (Get-Command 'npm.cmd').Source
$npmCli = Join-Path (Split-Path $npmCommand -Parent) 'node_modules\npm\bin\npm-cli.js'
if (-not (Test-Path $npmCli)) { throw "无法定位 npm CLI: $npmCli" }

Push-Location $frontendDir
try {
    & $nodeExe $npmCli ci
    if ($LASTEXITCODE -ne 0) { throw 'npm ci 失败。' }
    & $nodeExe $npmCli test
    if ($LASTEXITCODE -ne 0) { throw '前端测试失败。' }
    & $nodeExe $npmCli run build
    if ($LASTEXITCODE -ne 0) { throw '前端构建失败。' }
} finally { Pop-Location }

Push-Location $backendDir
try {
    & mvn -B verify
    if ($LASTEXITCODE -ne 0) { throw '后端测试或构建失败。' }
} finally { Pop-Location }

if (-not (Test-Path (Join-Path $distDir 'index.html'))) { throw '前端构建未产生 dist/index.html。' }
if (-not (Test-Path (Join-Path $backendDir 'target\jobproof-backend.jar'))) { throw '后端构建未产生目标 JAR。' }
if ([IO.Directory]::GetParent($webDir).FullName -ne $deployDir) { throw 'deploy/web 路径校验失败。' }

if (Test-Path $webDir) { Remove-Item -LiteralPath $webDir -Recurse -Force }
New-Item -ItemType Directory -Path $webDir | Out-Null
Copy-Item -Path (Join-Path $distDir '*') -Destination $webDir -Recurse -Force

Write-Host '测试与构建通过，deploy/web 和 backend/target/jobproof-backend.jar 已就绪。'
