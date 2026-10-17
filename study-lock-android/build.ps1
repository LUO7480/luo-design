param(
    [string]$JdkPath = (Join-Path $PSScriptRoot '.tools\jdk-17.0.20.1+1'),
    [string]$SdkPath = (Join-Path $PSScriptRoot '.tools\sdk')
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$taskJava = Join-Path $JdkPath 'bin\java.exe'
$taskJavac = Join-Path $JdkPath 'bin\javac.exe'
$taskTools = Join-Path $SdkPath 'build-tools\36.0.0'
$taskAndroid = Join-Path $SdkPath 'platforms\android-36\android.jar'
foreach ($taskRequired in @($taskJava, $taskJavac, $taskAndroid, (Join-Path $taskTools 'aapt2.exe'))) {
    if (-not (Test-Path -LiteralPath $taskRequired)) { throw "Missing tool: $taskRequired. Pass -JdkPath and -SdkPath for an installed JDK 17 and Android SDK 36." }
}
function Invoke-Checked([string]$Executable, [string[]]$Arguments) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Tool failed with exit code ${LASTEXITCODE}: $Executable" }
}
$taskBuild = Join-Path $PSScriptRoot ('build\sdk-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path "$taskBuild\classes", "$taskBuild\generated", "$taskBuild\dex", "$taskBuild\tests", 'dist' | Out-Null
Invoke-Checked $taskJavac @('-encoding','UTF-8','-d',"$taskBuild\tests",'app\src\main\java\cn\studylock\Schedule.java','app\src\main\java\cn\studylock\SessionTimes.java','app\src\main\java\cn\studylock\RecoveryCrypto.java','app\src\main\java\cn\studylock\RecoveryRules.java','app\src\main\java\cn\studylock\BlockingRules.java','app\src\main\java\cn\studylock\CallRules.java','app\src\main\java\cn\studylock\ForegroundTracker.java','tests\ForegroundTrackerTest.java','tests\ScheduleTest.java','tests\RecoveryTest.java','tests\BlockingRulesTest.java','tests\CallRulesTest.java')
Invoke-Checked $taskJava @('-cp',"$taskBuild\tests",'ScheduleTest')
Invoke-Checked $taskJava @('-cp',"$taskBuild\tests",'RecoveryTest')
Invoke-Checked $taskJava @('-cp',"$taskBuild\tests",'BlockingRulesTest')
Invoke-Checked $taskJava @('-cp',"$taskBuild\tests",'CallRulesTest')
Invoke-Checked $taskJava @('-cp',"$taskBuild\tests",'ForegroundTrackerTest')
Invoke-Checked (Join-Path $taskTools 'aapt2.exe') @('compile','--dir','app\src\main\res','-o',"$taskBuild\resources.zip")
# Gradle owns package/version/SDK metadata. Materialize it for the standalone SDK build too.
$taskConfig = Get-Content -LiteralPath 'app\build.gradle' -Raw
function Read-AndroidValue([string]$Pattern) {
    $taskMatch = [regex]::Match($taskConfig, $Pattern)
    if (-not $taskMatch.Success) { throw "Missing Android build value: $Pattern" }
    return $taskMatch.Groups[1].Value
}
[xml]$taskManifest = Get-Content -LiteralPath 'app\src\main\AndroidManifest.xml' -Raw
$taskNamespace = 'http://schemas.android.com/apk/res/android'
$taskManifest.manifest.SetAttribute('package', (Read-AndroidValue "applicationId\s+'([^']+)'"))
$taskManifest.manifest.SetAttribute('versionCode', $taskNamespace, (Read-AndroidValue 'versionCode\s+(\d+)')) | Out-Null
$taskManifest.manifest.SetAttribute('versionName', $taskNamespace, (Read-AndroidValue "versionName\s+'([^']+)'")) | Out-Null
$taskSdkElement = $taskManifest.CreateElement('uses-sdk')
$taskSdkElement.SetAttribute('minSdkVersion', $taskNamespace, (Read-AndroidValue 'minSdk\s+(\d+)')) | Out-Null
$taskSdkElement.SetAttribute('targetSdkVersion', $taskNamespace, (Read-AndroidValue 'targetSdk\s+(\d+)')) | Out-Null
$taskManifest.manifest.PrependChild($taskSdkElement) | Out-Null
$taskManifest.Save("$taskBuild\AndroidManifest.xml")
$taskApk = 'dist\学习锁屏-' + (Read-AndroidValue "versionName\s+'([^']+)'") + '.apk'
Invoke-Checked (Join-Path $taskTools 'aapt2.exe') @('link','-o',"$taskBuild\unsigned.apk",'-I',$taskAndroid,'--manifest',"$taskBuild\AndroidManifest.xml",'--java',"$taskBuild\generated",'--auto-add-overlay',"$taskBuild\resources.zip")
$taskSources = @(Get-ChildItem -LiteralPath 'app\src\main\java', "$taskBuild\generated" -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
$taskSourceList = Join-Path $taskBuild 'sources.txt'
[IO.File]::WriteAllLines($taskSourceList, @($taskSources | ForEach-Object { '"' + $_.Replace('\','/') + '"' }), (New-Object Text.UTF8Encoding($false)))
Invoke-Checked $taskJavac @('-J-Dfile.encoding=UTF-8','-encoding','UTF-8','--release','17','-classpath',$taskAndroid,'-d',"$taskBuild\classes",('@' + $taskSourceList))
Invoke-Checked (Join-Path $JdkPath 'bin\jar.exe') @('--create','--file',"$taskBuild\classes.jar",'-C',"$taskBuild\classes",'.')
Invoke-Checked $taskJava @('-cp',(Join-Path $taskTools 'lib\d8.jar'),'com.android.tools.r8.D8','--min-api','33','--lib',$taskAndroid,'--output',"$taskBuild\dex","$taskBuild\classes.jar")
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskArchive = [IO.Compression.ZipFile]::Open("$taskBuild\unsigned.apk", [IO.Compression.ZipArchiveMode]::Update)
try { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($taskArchive, "$taskBuild\dex\classes.dex", 'classes.dex') | Out-Null } finally { $taskArchive.Dispose() }
Invoke-Checked (Join-Path $taskTools 'zipalign.exe') @('-f','-p','4',"$taskBuild\unsigned.apk","$taskBuild\aligned.apk")
$taskKeyStore = Join-Path $PSScriptRoot '.tools\studylock.keystore'
if (-not (Test-Path -LiteralPath $taskKeyStore)) {
    Invoke-Checked (Join-Path $JdkPath 'bin\keytool.exe') @('-genkeypair','-keystore',$taskKeyStore,'-storepass','android','-keypass','android','-alias','studylock','-keyalg','RSA','-keysize','3072','-validity','3650','-dname','CN=StudyLock Local Development')
}
Invoke-Checked $taskJava @('-jar',(Join-Path $taskTools 'lib\apksigner.jar'),'sign','--ks',$taskKeyStore,'--ks-key-alias','studylock','--ks-pass','pass:android','--key-pass','pass:android','--out',$taskApk,"$taskBuild\aligned.apk")
Invoke-Checked $taskJava @('-jar',(Join-Path $taskTools 'lib\apksigner.jar'),'verify','--verbose',$taskApk)
Invoke-Checked (Join-Path $taskTools 'aapt2.exe') @('dump','badging',$taskApk)
Get-FileHash -LiteralPath $taskApk -Algorithm SHA256
