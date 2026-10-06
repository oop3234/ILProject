param([string]$GsonJar = $env:GSON_JAR)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (-not $GsonJar) {
    $cache = Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.10.1'
    $candidate = Get-ChildItem -LiteralPath $cache -Filter 'gson-2.10.1.jar' -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($candidate) { $GsonJar = $candidate.FullName }
}
if (-not $GsonJar -or -not (Test-Path -LiteralPath $GsonJar)) {
    throw 'Provide Gson 2.10.1 with -GsonJar <path> or GSON_JAR. No dependency is downloaded automatically.'
}
$buildDir = Join-Path $projectRoot 'app/build'
New-Item -ItemType Directory -Force -Path $buildDir | Out-Null
$mainFiles = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'app/src/main/kotlin') -Filter '*.kt' -Recurse | ForEach-Object FullName)
$testFiles = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'app/src/test/kotlin') -Filter '*.kt' -Recurse | ForEach-Object FullName)
$mainJar = Join-Path $buildDir 'indoor-core.jar'
$testJar = Join-Path $buildDir 'indoor-tests.jar'
# Argument files keep Windows batch wrappers from splitting classpaths at semicolons/spaces.
function Write-CompilerArgs([string]$path, [string[]]$arguments) {
    $quoted = $arguments | ForEach-Object { '"' + $_.Replace('\', '/') + '"' }
    [IO.File]::WriteAllLines($path, $quoted, [Text.UTF8Encoding]::new($false))
}
$mainArgs = Join-Path $buildDir 'main.args'
$testArgs = Join-Path $buildDir 'test.args'
Write-CompilerArgs $mainArgs ($mainFiles + @('-jvm-target', '17', '-classpath', $GsonJar, '-d', $mainJar))
& kotlinc "@$mainArgs"
if ($LASTEXITCODE -ne 0) { throw 'Core compilation failed' }
Write-CompilerArgs $testArgs ($testFiles + @('-jvm-target', '17', '-classpath', "$mainJar$([IO.Path]::PathSeparator)$GsonJar", '-include-runtime', '-d', $testJar))
& kotlinc "@$testArgs"
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& java -cp "$testJar$([IO.Path]::PathSeparator)$mainJar$([IO.Path]::PathSeparator)$GsonJar" indoor.CoreTestsKt (Join-Path $projectRoot 'floor2_map.json')
if ($LASTEXITCODE -ne 0) { throw 'Tests failed' }
