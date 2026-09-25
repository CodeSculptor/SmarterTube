<#
.SYNOPSIS
    Cut a signed SmarterTube phone-port (stmobile) release: bump version, build all
    four ABI APKs, verify the signing cert, tag, and publish a GitHub release.

.DESCRIPTION
    Automates the mechanical release steps documented in memory/release_signing.md so
    they can't drift. It is deliberately HUMAN-TRIGGERED and assumes you have already
    smoke-tested the build on a device (CI's `validate` proves the build, not playback).

    What it does NOT do: decide whether a release is warranted, pick the semver, or run
    the device smoke test. Those stay with you.

    Order is build-first: the release is compiled, signed, and cert-verified BEFORE any
    commit/push/tag/publish, so a version bump that doesn't build never reaches master.

    Run it from a CLEAN checkout (clean `git status`) whose HEAD is master or fast-forwards
    onto master — e.g. master itself, or a fresh worktree branched off the master tip. The
    signing key lives only locally (+ the backup); nothing is uploaded to the cloud.

.PARAMETER VersionName
    Full fork version following the beta-reset scheme (docs/VERSIONING.md):
    "v<major>.<minor>.<patch>[-<channel>.<n>]+st<upstream>", e.g. "v0.5.0-beta.1+st31.94".
    The +st<upstream> suffix must match the merged upstream engine (defaultConfig versionName);
    a mismatch warns but does not block.

.PARAMETER VersionCode
    Override the integer versionCode. Default = current stmobile versionCode + 1.
    Must be strictly greater than the current value (Android rejects a lower code for
    in-place upgrade).

.PARAMETER Prerelease
    Mark the GitHub release as a prerelease. Use for ALPHA only - beta/rc/stable are published as
    normal releases so they show as "Latest" (channel is in the tag; see docs/VERSIONING.md).

.PARAMETER NotesFile
    Path to a markdown file for the release body. If omitted, a minimal body is generated.

.PARAMETER DryRun
    Do everything up to and including the signed build + cert verification, then STOP —
    no commit, no push, no tag, no publish. Reverts the build.gradle bump on exit.

.PARAMETER Repo
    GitHub repo slug. Default: CodeSculptor/SmarterTube.

.EXAMPLE
    .\release.ps1 -VersionName v0.5.0-beta.1+st31.94 -NotesFile notes.md
.EXAMPLE
    .\release.ps1 -VersionName v0.5.0-beta.1+st31.94 -Prerelease -DryRun
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)] [string] $VersionName,
    [int]    $VersionCode = 0,
    [switch] $Prerelease,
    [string] $NotesFile,
    [switch] $DryRun,
    [string] $Repo = 'CodeSculptor/SmarterTube'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# --- Constants -------------------------------------------------------------------------
$ExpectedCertSha256 = '50fdb412c6e3b683bbd03f9f7a69c40f436b7769810310e30ec96a91259b98a2'
$KeyBackupDir       = 'C:\Users\steph\Backups\SmarterTube-release-key'
$Root               = $PSScriptRoot
$Gradle             = Join-Path $Root 'gradlew.bat'
$BuildGradle        = Join-Path $Root 'smarttubetv\build.gradle'
$ReleaseDir         = Join-Path $Root 'smarttubetv\build\outputs\apk\stmobile\release'

function Step($m) { Write-Host "`n==> $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "    OK: $m" -ForegroundColor Green }
function Die($m)  { Write-Host "    ERROR: $m" -ForegroundColor Red; exit 1 }

# --- Preconditions ---------------------------------------------------------------------
Step 'Checking preconditions'

if (-not (Test-Path $BuildGradle)) { Die "not at project root (no $BuildGradle). Run from the repo root." }

# Clean working tree
$dirty = git -C $Root status --porcelain
if ($dirty) { Die "working tree is dirty. Releases must be built from a clean checkout:`n$dirty" }

# Branch / fast-forwards onto master
$branch = (git -C $Root rev-parse --abbrev-ref HEAD).Trim()
git -C $Root fetch origin master --quiet
$head      = (git -C $Root rev-parse HEAD).Trim()
$originMaster = (git -C $Root rev-parse origin/master).Trim()
$mergeBase = (git -C $Root merge-base HEAD origin/master).Trim()
if ($branch -ne 'master' -and $mergeBase -ne $originMaster) {
    Die "HEAD ($branch) does not fast-forward onto origin/master (not branched off the master tip). Rebase first."
}
Ok "checkout clean, branch '$branch' fast-forwards onto master"

# JDK 17
$javaVer = (& java -version 2>&1 | Select-Object -First 1)
if ($javaVer -notmatch '"17\.') { Die "JDK 17 required (build_environment.md). Found: $javaVer" }
Ok "java: $javaVer"

# Android SDK
if (-not $env:ANDROID_HOME) {
    if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_HOME = $env:ANDROID_SDK_ROOT }
    elseif (Test-Path "$env:LOCALAPPDATA\Android\Sdk") { $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk" }
    else { Die 'Android SDK not found. Set ANDROID_HOME or create local.properties (sdk.dir).' }
}
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
Ok "Android SDK: $env:ANDROID_HOME"

# apksigner (highest non-rc build-tools)
$btDir = Join-Path $env:ANDROID_HOME 'build-tools'
$bt = Get-ChildItem $btDir -Directory | Where-Object { $_.Name -notmatch 'rc' } |
        Sort-Object { [version]($_.Name) } -Descending | Select-Object -First 1
if (-not $bt) { Die "no build-tools under $btDir" }
$ApkSigner = Join-Path $bt.FullName 'apksigner.bat'
if (-not (Test-Path $ApkSigner)) { Die "apksigner not found at $ApkSigner" }
Ok "apksigner: $($bt.Name)"

# gh auth
gh auth status 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { Die 'gh is not authenticated (gh auth login).' }
Ok 'gh authenticated'

# Submodules present
git -C $Root submodule update --init --recursive --quiet
Ok 'submodules initialized'

# Keystore at project root (copy from backup if missing — both files are gitignored)
$jks   = Join-Path $Root 'smartertube-release.jks'
$props = Join-Path $Root 'keystore.properties'
foreach ($pair in @(@($jks,'smartertube-release.jks'), @($props,'keystore.properties'))) {
    if (-not (Test-Path $pair[0])) {
        $src = Join-Path $KeyBackupDir $pair[1]
        if (-not (Test-Path $src)) { Die "missing $($pair[1]) at root and in backup ($KeyBackupDir)." }
        Copy-Item $src $pair[0]
        Write-Host "    copied $($pair[1]) from backup" -ForegroundColor DarkGray
    }
}
Ok 'release keystore + keystore.properties present at root'

# --- Version computation ----------------------------------------------------------------
Step 'Computing version'

$content = Get-Content $BuildGradle -Raw
# Isolate the stmobile flavor block so we never touch upstream's defaultConfig values.
$blockMatch = [regex]::Match($content, '(?s)stmobile\s*\{.*?\n        \}')
if (-not $blockMatch.Success) { Die 'could not locate the stmobile flavor block in build.gradle.' }
$block = $blockMatch.Value

$curCode = [int]([regex]::Match($block, 'versionCode\s+(\d+)').Groups[1].Value)
$curName = [regex]::Match($block, 'versionName\s+"([^"]+)"').Groups[1].Value
if ($VersionCode -eq 0) { $VersionCode = $curCode + 1 }
if ($VersionCode -le $curCode) { Die "versionCode $VersionCode is not greater than current $curCode (monotonic rule)." }

# Upstream-base sanity check: the +st<upstream> suffix should match defaultConfig versionName.
$engineDefault = [regex]::Match($content, 'versionName\s+"(\d[\d.]*)"').Groups[1].Value
$stSuffix = [regex]::Match($VersionName, '\+st(\d[\d.]*)$').Groups[1].Value
if ($engineDefault -and $stSuffix -and ($stSuffix -ne $engineDefault)) {
    Write-Host "    WARN: +st suffix '$stSuffix' != merged engine '$engineDefault' (defaultConfig). Continuing." -ForegroundColor Yellow
}

# Tag must not already exist
$tagExists = git -C $Root tag --list $VersionName
if ($tagExists) { Die "tag '$VersionName' already exists." }

Write-Host "    $curName ($curCode)  ->  $VersionName ($VersionCode)   prerelease=$($Prerelease.IsPresent)" -ForegroundColor White

# --- Apply the bump (in the isolated block only) ---------------------------------------
Step 'Bumping build.gradle (stmobile flavor)'
$newBlock = $block `
    -replace 'versionCode\s+\d+', "versionCode $VersionCode" `
    -replace 'versionName\s+"[^"]+"', "versionName `"$VersionName`""
$content = $content.Remove($blockMatch.Index, $blockMatch.Length).Insert($blockMatch.Index, $newBlock)
Set-Content -Path $BuildGradle -Value $content -NoNewline -Encoding UTF8
Ok 'bumped'

# --- Build -----------------------------------------------------------------------------
Step 'Building signed release APKs (assembleStmobileRelease)'
& $Gradle ':smarttubetv:assembleStmobileRelease' --no-daemon
if ($LASTEXITCODE -ne 0) { git -C $Root checkout -- $BuildGradle; Die 'gradle build failed (build.gradle bump reverted).' }

$apks = Get-ChildItem (Join-Path $ReleaseDir "SmarterTube-$($VersionName.Replace('+', '-'))-*.apk") -ErrorAction SilentlyContinue
if ($apks.Count -ne 4) {
    git -C $Root checkout -- $BuildGradle
    Die "expected 4 ABI APKs, found $($apks.Count) in $ReleaseDir (bump reverted)."
}
Ok "built 4 APKs: $($apks.Name -join ', ')"

# --- Verify signing cert ---------------------------------------------------------------
Step 'Verifying signing certificate'
$certOut = & $ApkSigner verify --print-certs $apks[0].FullName 2>&1
$gotSha  = ([regex]::Match(($certOut -join "`n"), 'SHA-256 digest:\s*([0-9a-fA-F]{64})').Groups[1].Value).ToLower()
if ($gotSha -ne $ExpectedCertSha256) {
    git -C $Root checkout -- $BuildGradle
    Die "cert SHA-256 mismatch!`n  expected $ExpectedCertSha256`n  got      $gotSha`n(build.gradle bump reverted — did the wrong keystore get used?)"
}
Ok "cert SHA-256 matches ($gotSha)"

# --- DryRun stops here -----------------------------------------------------------------
if ($DryRun) {
    git -C $Root checkout -- $BuildGradle
    Step 'DRY RUN complete'
    Write-Host "    Build + sign + cert verification PASSED. No commit/push/tag/publish performed." -ForegroundColor Green
    Write-Host "    build.gradle bump reverted. Re-run without -DryRun to ship $VersionName." -ForegroundColor Green
    exit 0
}

# --- Commit, push, tag, publish --------------------------------------------------------
Step 'Committing version bump'
git -C $Root add $BuildGradle
git -C $Root commit -m "chore(release): stmobile $VersionName (versionCode $VersionCode)"
if ($LASTEXITCODE -ne 0) { Die 'git commit failed.' }
$relCommit = (git -C $Root rev-parse HEAD).Trim()
Ok "committed $($relCommit.Substring(0,9))"

Step 'Pushing to master'
git -C $Root push origin "${relCommit}:refs/heads/master"
if ($LASTEXITCODE -ne 0) { Die 'push to master failed (non-fast-forward? rebase onto origin/master and retry).' }
Ok 'master updated'

Step 'Creating and pushing tag'
git -C $Root tag -a $VersionName -m $VersionName $relCommit
git -C $Root push origin $VersionName
if ($LASTEXITCODE -ne 0) { Die 'tag push failed.' }
Ok "tag $VersionName pushed"

Step 'Publishing GitHub release'
if ($NotesFile -and (Test-Path $NotesFile)) {
    $notesArg = @('--notes-file', $NotesFile)
} else {
    $body = "Phone/tablet build (stmobile flavor) of SmartTube — version ``$VersionName``.`n`n" +
            "Install the ABI matching your device, or the universal APK. In-place upgrade from a prior " +
            "SmarterTube release (same signing key). See README for Obtainium auto-updates."
    $tmp = New-TemporaryFile
    Set-Content -Path $tmp -Value $body -Encoding UTF8
    $notesArg = @('--notes-file', $tmp.FullName)
}
$ghArgs = @('release','create', $VersionName, '-R', $Repo, '--target','master', '--title', $VersionName) + $notesArg
if ($Prerelease) { $ghArgs += '--prerelease' }
$ghArgs += ($apks.FullName)
& gh @ghArgs
if ($LASTEXITCODE -ne 0) { Die "gh release create failed (tag $VersionName and master are already pushed — finish manually)." }

$url = gh release view $VersionName -R $Repo --json url --jq '.url'
Step 'DONE'
Write-Host "    Released $VersionName ($VersionCode)  ->  $url" -ForegroundColor Green
Write-Host "    Verify the listing and, for stable, confirm it's not marked prerelease." -ForegroundColor Green
