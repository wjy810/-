[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$runDir = Join-Path $projectRoot '.local-run'
$stateFile = Join-Path $runDir 'run-state.json'

if (-not (Test-Path $stateFile)) {
    Write-Host '没有找到本项目的运行状态，未停止任何进程。'
    exit 0
}

$state = Get-Content -Raw -LiteralPath $stateFile | ConvertFrom-Json
function Stop-TrackedProcess([object]$ProcessId, [string]$ExpectedName, [object]$ExpectedStartUtc) {
    $processIdText = "$ProcessId"
    if ($null -eq $ProcessId -or $processIdText -notmatch '^\d+$') {
        throw "运行状态包含无效 PID，拒绝执行: $ProcessId"
    }
    $process = Get-Process -Id ([int]$ProcessId) -ErrorAction SilentlyContinue
    if ($null -eq $process) { return }
    if ($process.ProcessName -ne $ExpectedName) {
        throw "PID $ProcessId 当前是 $($process.ProcessName)，与记录的 $ExpectedName 不符，拒绝停止。"
    }
    if ($ExpectedStartUtc -is [DateTime]) {
        $expectedStart = $ExpectedStartUtc.ToUniversalTime()
    } else {
        $expectedStart = [DateTimeOffset]::Parse(
            [string]$ExpectedStartUtc,
            [Globalization.CultureInfo]::InvariantCulture,
            [Globalization.DateTimeStyles]::RoundtripKind
        ).UtcDateTime
    }
    if ([Math]::Abs(($process.StartTime.ToUniversalTime() - $expectedStart).TotalSeconds) -gt 2) {
        throw "PID $ProcessId 的启动时间与记录不符，拒绝停止。"
    }
    & taskkill.exe /PID ([int]$ProcessId) /T /F *> $null
    if ($LASTEXITCODE -ne 0 -and (Get-Process -Id ([int]$ProcessId) -ErrorAction SilentlyContinue)) {
        throw "无法停止 PID $ProcessId。"
    }
}

Stop-TrackedProcess $state.frontendPid $state.frontendProcessName $state.frontendStartedAtUtc
Stop-TrackedProcess $state.backendPid $state.backendProcessName $state.backendStartedAtUtc

Remove-Item -LiteralPath $stateFile -Force
Write-Host 'JobProof AI 本地进程已停止。日志保留在 .local-run。'
exit 0
