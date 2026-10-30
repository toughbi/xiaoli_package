param(
    [string[]]$Tasks = @(":app:assembleDebug")
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$environmentRoot = Join-Path $projectRoot ".android-build"
$tempRoot = Join-Path $environmentRoot "temp"
$androidUserRoot = Join-Path $environmentRoot "android-user"

New-Item -ItemType Directory -Path $tempRoot, $androidUserRoot -Force | Out-Null

$env:JAVA_HOME = Join-Path $environmentRoot "jdk\jdk-17.0.2"
$env:ANDROID_HOME = Join-Path $environmentRoot "sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = Join-Path $environmentRoot "gradle-home"
$env:ANDROID_USER_HOME = $androidUserRoot
$env:TEMP = $tempRoot
$env:TMP = $tempRoot
$env:Path = "$($env:JAVA_HOME)\bin;$env:Path"
Remove-Item Env:ANDROID_PREFS_ROOT -ErrorAction SilentlyContinue

$gradle = Join-Path $environmentRoot "gradle\gradle-8.14.2\bin\gradle.bat"
$androidProject = Join-Path $projectRoot "android-app"

Push-Location $androidProject
try {
    & $gradle @Tasks --no-daemon
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
