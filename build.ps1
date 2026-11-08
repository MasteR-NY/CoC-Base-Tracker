param([string]$JavaHome, [string]$GradlePath, [switch]$SkipTests)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$releaseVersion = [regex]::Match((Get-Content -LiteralPath 'app\build.gradle.kts' -Raw), 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
if (!$releaseVersion) { throw 'Release version is missing.' }
$outputApk = "outputs\VillageClock-$releaseVersion.apk"
if (!$JavaHome) { $JavaHome = $env:JAVA_HOME }
if (!$JavaHome) {
    $portable = Get-ChildItem -LiteralPath '.tools\jdk' -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($portable) { $JavaHome = $portable.FullName }
}
if (!(Test-Path -LiteralPath "$JavaHome\bin\java.exe")) { throw 'Set JAVA_HOME to a JDK 21 or newer, or pass -JavaHome.' }
$env:JAVA_HOME = $JavaHome
if (!$GradlePath) {
    $gradleCommand = Get-Command gradle.bat -ErrorAction SilentlyContinue
    if ($gradleCommand) { $GradlePath = $gradleCommand.Source }
    else {
        $cached = Get-ChildItem -Path "$env:USERPROFILE\.gradle\wrapper\dists\gradle-9.7.1-bin\*\gradle-9.7.1\bin\gradle.bat" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($cached) { $GradlePath = $cached.FullName }
    }
}
if (!$GradlePath) { throw 'Install Gradle 9.7.1 or pass -GradlePath pointing to its gradle.bat.' }
$tasks = @(':app:lintRelease', ':app:assembleRelease')
if (!$SkipTests) { $tasks = @(':app:testDebugUnitTest') + $tasks }
& $GradlePath --no-daemon @tasks
if ($LASTEXITCODE -ne 0) { throw "Android build failed ($LASTEXITCODE)." }
$sdkRoot = $env:ANDROID_HOME
if (!$sdkRoot) { $sdkRoot = "$env:LOCALAPPDATA\Android\Sdk" }
$buildTools = Get-ChildItem -LiteralPath "$sdkRoot\build-tools" -Directory | Sort-Object Name -Descending | Select-Object -First 1
if (!$buildTools) { throw 'Android SDK build-tools are missing.' }
New-Item -ItemType Directory -Path '.private','outputs' -Force | Out-Null
$key = Join-Path $PSScriptRoot '.private\village-clock.p12'
$passwordFile = Join-Path $PSScriptRoot '.private\signing-password.txt'
if (!(Test-Path -LiteralPath $key)) {
    if (Test-Path -LiteralPath $passwordFile) { throw 'Signing key missing but its password file exists. Restore the original key before signing updates.' }
    $passwordBytes = New-Object byte[] 32
    [Security.Cryptography.RandomNumberGenerator]::Fill($passwordBytes)
    $env:VILLAGE_CLOCK_SIGN_PASSWORD = [Convert]::ToBase64String($passwordBytes)
    [IO.File]::WriteAllText($passwordFile, $env:VILLAGE_CLOCK_SIGN_PASSWORD)
    & "$JavaHome\bin\keytool.exe" -genkeypair -keystore $key -storetype PKCS12 -alias village-clock -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Village Clock Personal Build' -storepass:env VILLAGE_CLOCK_SIGN_PASSWORD -keypass:env VILLAGE_CLOCK_SIGN_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Could not create private signing key.' }
} else {
    if (!(Test-Path -LiteralPath $passwordFile)) { throw 'Restore .private/signing-password.txt before signing updates.' }
    $env:VILLAGE_CLOCK_SIGN_PASSWORD = [IO.File]::ReadAllText($passwordFile).Trim()
}
try {
    & "$($buildTools.FullName)\zipalign.exe" -f -p 4 'app\build\outputs\apk\release\app-release-unsigned.apk' 'outputs\VillageClock-aligned.apk'
    if ($LASTEXITCODE -ne 0) { throw 'zipalign failed.' }
    & "$JavaHome\bin\java.exe" -jar "$($buildTools.FullName)\lib\apksigner.jar" sign --ks $key --ks-key-alias village-clock --ks-pass env:VILLAGE_CLOCK_SIGN_PASSWORD --key-pass env:VILLAGE_CLOCK_SIGN_PASSWORD --out $outputApk 'outputs\VillageClock-aligned.apk'
    if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }
    & "$JavaHome\bin\java.exe" -jar "$($buildTools.FullName)\lib\apksigner.jar" verify --verbose --print-certs $outputApk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    Get-FileHash -LiteralPath $outputApk -Algorithm SHA256
} finally { Remove-Item Env:VILLAGE_CLOCK_SIGN_PASSWORD -ErrorAction SilentlyContinue }
