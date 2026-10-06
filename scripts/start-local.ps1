[CmdletBinding()]
param([switch]$Rebuild)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$backendDir = Join-Path $projectRoot 'backend'
$frontendDir = Join-Path $projectRoot 'frontend'
$runDir = Join-Path $projectRoot '.local-run'
$stateFile = Join-Path $runDir 'run-state.json'
$jarFile = Join-Path $backendDir 'target\jobproof-backend.jar'

function Assert-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "缺少命令 '$Name'，请先按 README 安装运行环境。"
    }
}

function Assert-PortFree([int]$Port) {
    if (Get-Command Get-NetTCPConnection -ErrorAction SilentlyContinue) {
        $listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
        if ($listener) { throw "端口 $Port 已被占用，请先停止对应服务。" }
    }
}

function Stop-StartedProcess($Process) {
    if ($null -ne $Process -and -not $Process.HasExited) {
        & taskkill.exe /PID $Process.Id /T /F *> $null
    }
}

Assert-Command 'java'
Assert-Command 'node'
Assert-Command 'npm.cmd'
Assert-PortFree 18081
Assert-PortFree 5174

$javaVersion = (& java -version 2>&1 | Select-Object -First 1) -join ''
$nodeVersion = (& node -p "process.versions.node")
if ([version]$nodeVersion -lt [version]'24.15.0' -or [version]$nodeVersion -ge [version]'25.0.0') {
    throw "需要 Node.js >=24.15.0 且 <25，当前为 $nodeVersion。"
}
$nodeExe = (Get-Command 'node').Source
$npmCommand = (Get-Command 'npm.cmd').Source
$npmCli = Join-Path (Split-Path $npmCommand -Parent) 'node_modules\npm\bin\npm-cli.js'
if (-not (Test-Path $npmCli)) { throw "无法定位 npm CLI: $npmCli" }

if (Test-Path $stateFile) {
    throw "发现 $stateFile。若项目已停止，请先运行 scripts\stop-local.ps1 清理状态。"
}

if ($Rebuild -or -not (Test-Path $jarFile)) {
    Assert-Command 'mvn'
    Push-Location $backendDir
    try { & mvn -B verify; if ($LASTEXITCODE -ne 0) { throw '后端构建或测试失败。' } }
    finally { Pop-Location }
}

if ($Rebuild -or -not (Test-Path (Join-Path $frontendDir 'node_modules'))) {
    Push-Location $frontendDir
    try { & $nodeExe $npmCli ci; if ($LASTEXITCODE -ne 0) { throw '前端依赖安装失败。' } }
    finally { Pop-Location }
}

New-Item -ItemType Directory -Force -Path $runDir | Out-Null
$backendOut = Join-Path $runDir 'backend.out.log'
$backendErr = Join-Path $runDir 'backend.err.log'
$frontendOut = Join-Path $runDir 'frontend.out.log'
$frontendErr = Join-Path $runDir 'frontend.err.log'
$backend = $null
$frontend = $null

try {
    $backendArgs = @(
        '-jar', "`"$jarFile`"",
        '--spring.profiles.active=dev',
        '--server.port=18081',
        '--jobproof.cors.origins=http://127.0.0.1:5174',
        '--jobproof.storage.type=local',
        "`"--jobproof.storage.local-dir=$backendDir\.local-data\files`"",
        '--jobproof.verification.email-provider=dev',
        '--jobproof.verification.sms-provider=dev',
        '--jobproof.dev.admin.enabled=true',
        '--jobproof.dev.admin.alias=admin',
        '--jobproof.dev.admin.email=admin@jobproof.local',
        '--jobproof.dev.admin.password=admin',
        '--jobproof.dev.seeker.enabled=true',
        '--jobproof.dev.seeker.alias=seeker',
        '--jobproof.dev.seeker.email=seeker@jobproof.local',
        '--jobproof.dev.seeker.password=seeker123'
    )
    $backend = Start-Process -FilePath 'java' -ArgumentList $backendArgs -WorkingDirectory $backendDir `
        -RedirectStandardOutput $backendOut -RedirectStandardError $backendErr -WindowStyle Hidden -PassThru

    $frontendArgs = @("`"$npmCli`"", 'run', 'dev', '--', '--host', '127.0.0.1', '--port', '5174')
    $frontend = Start-Process -FilePath $nodeExe -ArgumentList $frontendArgs `
        -WorkingDirectory $frontendDir -RedirectStandardOutput $frontendOut -RedirectStandardError $frontendErr `
        -WindowStyle Hidden -PassThru

    [ordered]@{
        backendPid = $backend.Id
        backendProcessName = $backend.ProcessName
        backendStartedAtUtc = $backend.StartTime.ToUniversalTime().ToString('o')
        frontendPid = $frontend.Id
        frontendProcessName = $frontend.ProcessName
        frontendStartedAtUtc = $frontend.StartTime.ToUniversalTime().ToString('o')
        startedAt = (Get-Date).ToString('o')
    } | ConvertTo-Json | Set-Content -Encoding UTF8 -LiteralPath $stateFile

    $backendReady = $false
    $frontendReady = $false
    for ($attempt = 1; $attempt -le 90; $attempt++) {
        if ($backend.HasExited) { throw "后端提前退出，请查看 $backendErr" }
        if ($frontend.HasExited) { throw "前端提前退出，请查看 $frontendErr" }
        try {
            $health = Invoke-RestMethod -Uri 'http://127.0.0.1:18081/api/v1/health' -TimeoutSec 2
            $backendReady = ($health.status -eq 'UP' -or $health.data.status -eq 'UP')
        } catch { $backendReady = $false }
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:5174/' -TimeoutSec 2
            $frontendReady = ($response.StatusCode -eq 200)
        } catch { $frontendReady = $false }
        if ($backendReady -and $frontendReady) { break }
        Start-Sleep -Seconds 2
    }
    if (-not ($backendReady -and $frontendReady)) {
        throw "服务未在 180 秒内就绪，请查看 $runDir 下的日志。"
    }

    Write-Host "JobProof AI 已启动: http://127.0.0.1:5174/"
    Write-Host "管理员: admin / admin（仅本地开发）"
    Write-Host "停止命令: .\scripts\stop-local.ps1"
    Write-Host "运行环境: $javaVersion; Node $nodeVersion"
} catch {
    Stop-StartedProcess $frontend
    Stop-StartedProcess $backend
    Remove-Item -LiteralPath $stateFile -Force -ErrorAction SilentlyContinue
    throw
}
