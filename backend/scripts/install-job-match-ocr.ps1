param(
    [string]$TargetDirectory = (Join-Path (Split-Path $PSScriptRoot -Parent) '.local-data\ocr\tessdata')
)

$ErrorActionPreference = 'Stop'
$models = @(
    @{ Name = 'eng.traineddata'; Sha256 = '7D4322BD2A7749724879683FC3912CB542F19906C83BCC1A52132556427170B2' },
    @{ Name = 'chi_sim.traineddata'; Sha256 = 'A5FCB6F0DB1E1D6D8522F39DB4E848F05984669172E584E8D76B6B3141E1F730' }
)
$baseUrl = 'https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/4.1.0'

New-Item -ItemType Directory -Force -Path $TargetDirectory | Out-Null
foreach ($model in $models) {
    $target = Join-Path $TargetDirectory $model.Name
    $download = "$target.download"
    Invoke-WebRequest -Uri "$baseUrl/$($model.Name)" -OutFile $download
    $actual = (Get-FileHash -LiteralPath $download -Algorithm SHA256).Hash
    if ($actual -ne $model.Sha256) {
        Remove-Item -LiteralPath $download -Force
        throw "OCR model checksum mismatch for $($model.Name): $actual"
    }
    Move-Item -LiteralPath $download -Destination $target -Force
}

Write-Host "Installed pinned Tesseract tessdata_fast 4.1.0 models to $TargetDirectory"
