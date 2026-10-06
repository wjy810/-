param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$AdminAccount = 'admin',
    [string]$AdminPassword = $env:JOBPROOF_ADMIN_PASSWORD,
    [string]$ManifestPath = (Join-Path $PSScriptRoot '..\..\docs\changelog\verified-history-v1.json')
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($AdminPassword)) {
    $AdminPassword = 'admin'
}

function Invoke-JobProofApi {
    param(
        [Parameter(Mandatory)] [string]$Method,
        [Parameter(Mandatory)] [string]$Path,
        [object]$Body
    )

    $parameters = @{
        Uri = "$BaseUrl$Path"
        Method = $Method
        WebSession = $script:Session
        ContentType = 'application/json; charset=utf-8'
    }
    if ($null -ne $Body) {
        $parameters.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }
    (Invoke-RestMethod @parameters).data
}

$script:Session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$loginBody = @{ email = $AdminAccount; password = $AdminPassword } | ConvertTo-Json -Compress
Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -WebSession $script:Session `
    -ContentType 'application/json; charset=utf-8' -Body $loginBody | Out-Null

$manifest = Get-Content -Raw -Encoding UTF8 -LiteralPath $ManifestPath | ConvertFrom-Json
$existing = Invoke-JobProofApi -Method Get -Path '/api/v1/admin/changelog?size=100'
$byVersion = @{}
foreach ($item in $existing.items) {
    $byVersion[$item.versionLabel] = $item
}

foreach ($release in $manifest) {
    $current = $byVersion[$release.versionLabel]
    if ($null -eq $current) {
        $draft = [ordered]@{
            versionLabel = $release.versionLabel
            title = $release.title
            summary = $release.summary
            releaseType = $release.releaseType
            audience = $release.audience
            modules = @($release.modules)
            ctaLabel = $release.ctaLabel
            ctaPath = $release.ctaPath
            showWhatsNew = [bool]$release.showWhatsNew
            sendNotification = [bool]$release.sendNotification
            sections = @($release.sections)
            expectedVersion = 0
        }
        $created = Invoke-JobProofApi -Method Post -Path '/api/v1/admin/changelog' -Body $draft
        $current = $created.release
        Write-Host "Created $($release.versionLabel)"
    }

    if ($current.status -eq 'DRAFT') {
        if ($null -ne $release.publishedAt) {
            $published = Invoke-JobProofApi -Method Post `
                -Path "/api/v1/admin/changelog/$($current.id)/publish-history" `
                -Body @{ expectedVersion = $current.versionNo; publishedAt = $release.publishedAt }
        } else {
            $published = Invoke-JobProofApi -Method Post `
                -Path "/api/v1/admin/changelog/$($current.id)/publish" `
                -Body @{ expectedVersion = $current.versionNo }
        }
        $current = $published.release
        Write-Host "Published $($release.versionLabel) at $($current.publishedAt)"
    } else {
        Write-Host "Skipped $($release.versionLabel): status=$($current.status)"
    }

    $byVersion[$release.versionLabel] = $current
}

$public = Invoke-JobProofApi -Method Get -Path '/api/v1/updates?size=100'
Write-Host "Public changelog contains $($public.total) releases."
