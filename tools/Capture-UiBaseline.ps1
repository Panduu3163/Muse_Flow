param(
    [Parameter(Mandatory = $true)][string]$Label,
    [string]$PackageName = 'com.aistudio.museflow.kqfzyw.beta',
    [string]$OutputDirectory = 'artifacts/ui-baselines'
)

$ErrorActionPreference = 'Stop'
$adb = (Get-Command adb -ErrorAction Stop).Source
$devices = @(& $adb devices | Select-String '\sdevice$')
if ($devices.Count -ne 1) {
    throw "Connect exactly one authorized Android device. Found $($devices.Count)."
}

$safeLabel = $Label -replace '[^a-zA-Z0-9_-]', '-'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$directory = Join-Path $OutputDirectory "$stamp-$safeLabel"
New-Item -ItemType Directory -Force -Path $directory | Out-Null

& $adb shell dumpsys gfxinfo $PackageName reset | Out-Null
Write-Host "Frame counter reset. Perform '$Label' on the device, then press Enter."
Read-Host | Out-Null

$remoteScreenshot = "/sdcard/Download/museflow-ui-$stamp.png"
& $adb shell screencap -p $remoteScreenshot | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Screenshot capture failed.' }
& $adb pull $remoteScreenshot (Join-Path $directory 'screen.png') | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Screenshot transfer failed.' }
& $adb shell rm $remoteScreenshot | Out-Null

& $adb shell dumpsys gfxinfo $PackageName framestats |
    Set-Content -Encoding utf8 (Join-Path $directory 'gfxinfo.txt')
& $adb shell getprop ro.product.model |
    Set-Content -Encoding utf8 (Join-Path $directory 'device-model.txt')
& $adb shell getprop ro.build.version.release |
    Set-Content -Encoding utf8 (Join-Path $directory 'android-version.txt')
Write-Host "Saved screenshot and frame data to $directory"
