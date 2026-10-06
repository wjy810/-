param(
    [Parameter(Mandatory = $true)]
    [string]$ManifestPath
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$manifest = Get-Content -Raw -LiteralPath $ManifestPath | ConvertFrom-Json
$results = [System.Collections.Generic.List[object]]::new()
$word = $null

try {
    $word = New-Object -ComObject Word.Application
    $word.Visible = $false
    $word.DisplayAlerts = 0
    $word.AutomationSecurity = 3
    $word.Options.SaveNormalPrompt = $false

    foreach ($item in $manifest) {
        $document = $null
        try {
            $document = $word.Documents.Open([string]$item.inputPath, $false, $true)
            $document.ExportAsFixedFormat([string]$item.outputPath, 17)
            $results.Add([pscustomobject]@{
                id = [string]$item.id
                success = $true
                errorCode = $null
            })
        }
        catch {
            $results.Add([pscustomobject]@{
                id = [string]$item.id
                success = $false
                errorCode = 'WORD_EXPORT_FAILED'
            })
        }
        finally {
            if ($null -ne $document) {
                $document.Close(0)
                [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($document)
            }
        }
    }
}
catch {
    foreach ($item in $manifest) {
        if (-not ($results | Where-Object { $_.id -eq [string]$item.id })) {
            $results.Add([pscustomobject]@{
                id = [string]$item.id
                success = $false
                errorCode = 'WORD_ENGINE_UNAVAILABLE'
            })
        }
    }
}
finally {
    if ($null -ne $word) {
        $word.Quit()
        [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($word)
    }
    [GC]::Collect()
    [GC]::WaitForPendingFinalizers()
}

@($results) | ConvertTo-Json -Compress
