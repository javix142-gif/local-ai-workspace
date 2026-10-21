[CmdletBinding()]
param(
    [string]$PackageName = 'com.localai.workspace.debug',
    [string]$OutputDirectory,
    [switch]$OpenApp,
    [switch]$CaptureConversationEvidence
)

$ErrorActionPreference = 'Stop'
$script:CommandRecords = New-Object System.Collections.Generic.List[object]
$script:Serial = $null
$script:OutputRoot = $null
$script:RepoRoot = $null

function Write-Utf8File {
    param([string]$Path, [string]$Text)
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Text, $encoding)
}

function Get-RepositoryRoot {
    $directory = Get-Item -LiteralPath $PSScriptRoot
    while ($null -ne $directory) {
        if ((Test-Path -LiteralPath (Join-Path $directory.FullName '.git')) -or
            (Test-Path -LiteralPath (Join-Path $directory.FullName 'AGENTS.md'))) {
            return [System.IO.Path]::GetFullPath($directory.FullName).TrimEnd([char[]]@('\', '/'))
        }
        $directory = $directory.Parent
    }
    throw 'REPOSITORY_ROOT_NOT_FOUND'
}

function Get-DefaultOutputDirectory {
    $base = $env:LOCALAPPDATA
    if ([string]::IsNullOrWhiteSpace($base)) { $base = [System.IO.Path]::GetTempPath() }
    $stamp = [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')
    return Join-Path $base "LocalAiWorkspace\device-smoke\$stamp"
}

function Assert-OutputOutsideRepository {
    param([string]$Path)
    $fullPath = [System.IO.Path]::GetFullPath($Path).TrimEnd([char[]]@('\', '/'))
    $repoPrefix = $script:RepoRoot + [System.IO.Path]::DirectorySeparatorChar
    if ($fullPath.Equals($script:RepoRoot, [StringComparison]::OrdinalIgnoreCase) -or
        $fullPath.StartsWith($repoPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'OUTPUT_DIRECTORY_MUST_BE_OUTSIDE_REPOSITORY'
    }
    if (Test-Path -LiteralPath $fullPath) {
        if (@(Get-ChildItem -LiteralPath $fullPath -Force).Count -gt 0) {
            throw 'OUTPUT_DIRECTORY_MUST_BE_EMPTY_TO_AVOID_OVERWRITE'
        }
    }
    [void][System.IO.Directory]::CreateDirectory($fullPath)
    $script:OutputRoot = $fullPath
}

function ConvertTo-CommandLine {
    param([string[]]$Arguments)
    foreach ($argument in $Arguments) {
        if ($argument -match '[\s"]') { throw 'UNEXPECTED_COMPLEX_NATIVE_ARGUMENT' }
    }
    return [string]::Join(' ', $Arguments)
}

function Invoke-NativeText {
    param(
        [string]$FilePath,
        [string[]]$Arguments,
        [int]$TimeoutSeconds = 20
    )
    $start = New-Object System.Diagnostics.ProcessStartInfo
    $start.FileName = $FilePath
    $start.Arguments = ConvertTo-CommandLine $Arguments
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $start
    if (-not $process.Start()) { throw 'PROCESS_START_FAILED' }
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
        try { $process.Kill() } catch {}
        $process.WaitForExit()
        throw "COMMAND_TIMEOUT after ${TimeoutSeconds}s"
    }
    $stdout = $stdoutTask.GetAwaiter().GetResult()
    $stderr = $stderrTask.GetAwaiter().GetResult()
    return [pscustomobject]@{
        ExitCode = $process.ExitCode
        Stdout = $stdout
        Stderr = $stderr
    }
}

function Find-Adb {
    $command = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($null -eq $command) { $command = Get-Command adb -ErrorAction SilentlyContinue }
    if ($null -ne $command) { return $command.Source }
    foreach ($sdk in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if (-not [string]::IsNullOrWhiteSpace($sdk)) {
            $candidate = Join-Path $sdk 'platform-tools\adb.exe'
            if (Test-Path -LiteralPath $candidate) { return $candidate }
        }
    }
    throw 'ADB_NOT_FOUND'
}

function Get-MaskedSerial {
    if ([string]::IsNullOrWhiteSpace($script:Serial)) { return '<not-selected>' }
    if ($script:Serial.Length -le 4) { return '…' + $script:Serial }
    return '…' + $script:Serial.Substring($script:Serial.Length - 4)
}

function Get-SanitizedDiagnosticText {
    param([string]$Text)
    if ([string]::IsNullOrWhiteSpace($Text)) { return $null }
    $safe = $Text.Trim()
    if ($script:Serial) { $safe = $safe.Replace($script:Serial, '<serial-redacted>') }
    $safe = $safe -replace '(?i)[A-Z]:\\Users\\[^\\\s]+', '<user-path>'
    $safe = (($safe -split "`r?`n" | Select-Object -First 4) -join ' | ')
    if ($safe.Length -gt 320) { $safe = $safe.Substring(0, 320) }
    return $safe
}

function Invoke-Adb {
    param(
        [string[]]$Arguments,
        [string]$Name,
        [switch]$AllowNonZero,
        [int]$TimeoutSeconds = 20
    )
    try {
        $result = Invoke-NativeText -FilePath $script:AdbPath -Arguments $Arguments -TimeoutSeconds $TimeoutSeconds
    } catch {
        $safeError = Get-SanitizedDiagnosticText -Text $_.Exception.Message
        $safeArguments = @($Arguments | ForEach-Object {
            if ($script:Serial -and $_ -eq $script:Serial) { '<serial-redacted>' } else { $_ }
        })
        $script:CommandRecords.Add([pscustomobject]@{
            timeUtc = [DateTime]::UtcNow.ToString('o')
            operation = $Name
            command = 'adb ' + [string]::Join(' ', $safeArguments)
            exitCode = $null
            error = $safeError
        })
        throw "ADB_COMMAND_ERROR operation=$Name detail=$safeError"
    }
    $safeArguments = @($Arguments | ForEach-Object {
        if ($script:Serial -and $_ -eq $script:Serial) { '<serial-redacted>' } else { $_ }
    })
    $script:CommandRecords.Add([pscustomobject]@{
        timeUtc = [DateTime]::UtcNow.ToString('o')
        operation = $Name
        command = 'adb ' + [string]::Join(' ', $safeArguments)
        exitCode = $result.ExitCode
        stderrSummary = Get-SanitizedDiagnosticText -Text $result.Stderr
    })
    if ($result.ExitCode -ne 0 -and -not $AllowNonZero) {
        throw "ADB_COMMAND_FAILED operation=$Name exitCode=$($result.ExitCode)"
    }
    return $result
}

function Get-OneAuthorizedDevice {
    $result = Invoke-NativeText -FilePath $script:AdbPath -Arguments @('devices', '-l') -TimeoutSeconds 20
    $script:CommandRecords.Add([pscustomobject]@{
        timeUtc = [DateTime]::UtcNow.ToString('o')
        operation = 'adb-devices'
        command = 'adb devices -l'
        exitCode = $result.ExitCode
        stderrSummary = Get-SanitizedDiagnosticText -Text $result.Stderr
    })
    if ($result.ExitCode -ne 0) {
        $detail = Get-SanitizedDiagnosticText -Text $result.Stderr
        throw "ADB_DEVICES_FAILED exitCode=$($result.ExitCode) detail=$detail"
    }
    $records = @()
    foreach ($line in ($result.Stdout -split "`r?`n")) {
        if ($line -match '^\s*(\S+)\s+(device|unauthorized|offline)(?:\s+(.*))?$') {
            $records += [pscustomobject]@{ Serial = $Matches[1]; State = $Matches[2]; Details = $Matches[3] }
        }
    }
    $authorized = @($records | Where-Object { $_.State -eq 'device' })
    if ($authorized.Count -ne 1) {
        $states = @($records | ForEach-Object { $_.State }) -join ','
        throw "EXPECTED_EXACTLY_ONE_AUTHORIZED_DEVICE found=$($authorized.Count) states=$states"
    }
    $script:Serial = $authorized[0].Serial
    return $authorized[0]
}

function Get-AdbValue {
    param([string]$Name, [string[]]$Arguments, [switch]$AllowNonZero)
    $result = Invoke-Adb -Arguments $Arguments -Name $Name -AllowNonZero:$AllowNonZero
    return $result.Stdout.Trim()
}

function Get-ProcessState {
    $processes = @()
    foreach ($suffix in @('', ':litert', ':python')) {
        $name = $PackageName + $suffix
        $pidText = Get-AdbValue -Name "pidof-$suffix" -Arguments @('-s', $script:Serial, 'shell', 'pidof', $name) -AllowNonZero
        $processes += [pscustomobject]@{
            process = if ($suffix -eq '') { 'main' } else { $suffix.TrimStart(':') }
            state = if ([string]::IsNullOrWhiteSpace($pidText)) { 'NOT_RUNNING' } else { 'RUNNING' }
        }
    }
    return $processes
}

function Get-SafeAppLogLines {
    param([string]$RawLog)
    $safe = New-Object System.Collections.Generic.List[string]
    foreach ($line in ($RawLog -split "`r?`n")) {
        if ($line -match '^(?<time>\d\d-\d\d \d\d:\d\d:\d\d\.\d+)\s+\d+\s+\d+\s+(?<priority>[VDIWEF])\s+(?<tag>LocalAI/LiteRT|LocalAI/ChatTrace|LocalAI/Context|LocalAI/Semantic|LocalAI/Runtime):\s*(?<message>.*)$') {
            $time = $Matches.time
            $priority = $Matches.priority
            $tag = $Matches.tag
            $message = $Matches.message
            if ($tag -eq 'LocalAI/LiteRT') {
                try {
                    $jsonEvent = $message | ConvertFrom-Json
                    $safe.Add("$time $priority $tag event=$($jsonEvent.event) stage=$($jsonEvent.stage) elapsedMs=$($jsonEvent.elapsedMs) appVersion=$($jsonEvent.appVersion)")
                } catch {
                    $safe.Add("$time $priority $tag event=<unparsed-redacted>")
                }
            } elseif ($message -match '^(?<operation>read_failed|write_failed|warm_cleanup_error|index_recovery_failed|benchmark_recovery_failed|legacy_memory_migration_failed|memory_recovery_failed|benchmark_failed) type=(?<type>[A-Za-z0-9_.$]+)$') {
                $safe.Add("$time $priority $tag operation=$($Matches.operation) errorType=$($Matches.type)")
            } else {
                $safe.Add("$time $priority $tag event=<message-redacted>")
            }
        }
    }
    return $safe
}

function Save-ConversationScreenshot {
    param([string]$Path)
    $start = New-Object System.Diagnostics.ProcessStartInfo
    $start.FileName = $script:AdbPath
    $start.Arguments = "-s $script:Serial exec-out screencap -p"
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $start
    if (-not $process.Start()) { throw 'SCREENSHOT_PROCESS_START_FAILED' }
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $stream = [System.IO.File]::Create($Path)
    try {
        $process.StandardOutput.BaseStream.CopyTo($stream)
    } finally {
        $stream.Dispose()
    }
    $process.WaitForExit()
    $stderr = $stderrTask.GetAwaiter().GetResult()
    $script:CommandRecords.Add([pscustomobject]@{
        timeUtc = [DateTime]::UtcNow.ToString('o')
        operation = 'explicit-manual-conversation-screenshot'
        command = 'adb -s <serial-redacted> exec-out screencap -p'
        exitCode = $process.ExitCode
        stderrSummary = Get-SanitizedDiagnosticText -Text $stderr
    })
    if ($process.ExitCode -ne 0) { throw 'SCREENSHOT_CAPTURE_FAILED' }
    $bytes = [System.IO.File]::ReadAllBytes($Path)
    $pngMagic = [byte[]](137, 80, 78, 71, 13, 10, 26, 10)
    if ($bytes.Length -lt 8) { throw 'SCREENSHOT_INVALID_PNG' }
    for ($index = 0; $index -lt 8; $index++) {
        if ($bytes[$index] -ne $pngMagic[$index]) { throw 'SCREENSHOT_INVALID_PNG' }
    }
}

function Write-Manifest {
    $lines = New-Object System.Collections.Generic.List[string]
    foreach ($file in @(Get-ChildItem -LiteralPath $script:OutputRoot -File | Where-Object { $_.Name -ne 'SHA256SUMS' } | Sort-Object Name)) {
        $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        $lines.Add("$hash  $($file.Name)")
    }
    Write-Utf8File -Path (Join-Path $script:OutputRoot 'SHA256SUMS') -Text (($lines -join "`n") + "`n")
}

$script:RepoRoot = Get-RepositoryRoot
if ([string]::IsNullOrWhiteSpace($PackageName) -or $PackageName -notmatch '^[A-Za-z0-9_.]+$') {
    throw 'PACKAGE_NAME_INVALID'
}
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) { $OutputDirectory = Get-DefaultOutputDirectory }
Assert-OutputOutsideRepository -Path $OutputDirectory
$script:Failure = $null
$script:Effects = New-Object System.Collections.Generic.List[string]
$script:Effects.Add('ADB read-only inspection')

try {
    $script:AdbPath = Find-Adb
    $device = Get-OneAuthorizedDevice
    $deviceInfo = [ordered]@{
        model = Get-AdbValue -Name 'model' -Arguments @('-s', $script:Serial, 'shell', 'getprop', 'ro.product.model')
        manufacturer = Get-AdbValue -Name 'manufacturer' -Arguments @('-s', $script:Serial, 'shell', 'getprop', 'ro.product.manufacturer')
        product = Get-AdbValue -Name 'product' -Arguments @('-s', $script:Serial, 'shell', 'getprop', 'ro.product.name')
        androidRelease = Get-AdbValue -Name 'android-release' -Arguments @('-s', $script:Serial, 'shell', 'getprop', 'ro.build.version.release')
        apiLevel = Get-AdbValue -Name 'api-level' -Arguments @('-s', $script:Serial, 'shell', 'getprop', 'ro.build.version.sdk')
        primaryAbi = Get-AdbValue -Name 'primary-abi' -Arguments @('-s', $script:Serial, 'shell', 'getprop', 'ro.product.cpu.abi')
        serialMasked = Get-MaskedSerial
        adbState = $device.State
    }

    $packagePath = Get-AdbValue -Name 'package-installed' -Arguments @('-s', $script:Serial, 'shell', 'pm', 'path', $PackageName) -AllowNonZero
    if ([string]::IsNullOrWhiteSpace($packagePath)) { throw 'PACKAGE_NOT_INSTALLED' }
    $packageDump = Get-AdbValue -Name 'package-metadata' -Arguments @('-s', $script:Serial, 'shell', 'dumpsys', 'package', $PackageName)
    $versionCode = $null
    $versionName = $null
    if ($packageDump -match 'versionCode=(\d+)') { $versionCode = [int]$Matches[1] }
    if ($packageDump -match 'versionName=([^\s]+)') { $versionName = $Matches[1] }
    $permissionNames = @(
        'android.permission.READ_CALENDAR',
        'android.permission.READ_CONTACTS',
        'com.android.alarm.permission.SET_ALARM'
    )
    $permissionResults = @()
    foreach ($permission in $permissionNames) {
        $check = Get-AdbValue -Name "permission-$permission" -Arguments @('-s', $script:Serial, 'shell', 'pm', 'check-permission', $permission, $PackageName) -AllowNonZero
        $permissionResults += [pscustomobject]@{ permission = $permission; result = $check }
    }
    $requestedLines = @($packageDump -split "`r?`n" | Where-Object {
        $line = $_
        $permissionNames | Where-Object { $line -match [regex]::Escape($_) }
    } | ForEach-Object { $_.Trim() } | Select-Object -Unique)
    $processState = Get-ProcessState

    if ($OpenApp) {
        [void](Invoke-Adb -Arguments @('-s', $script:Serial, 'shell', 'monkey', '-p', $PackageName, '1') -Name 'open-app')
        $script:Effects.Add('Optional app launch only')
        $deadline = [DateTime]::UtcNow.AddSeconds(12)
        do {
            Start-Sleep -Milliseconds 500
            $processState = Get-ProcessState
            if (($processState | Where-Object { $_.process -eq 'main' -and $_.state -eq 'RUNNING' }).Count -gt 0) { break }
        } while ([DateTime]::UtcNow -lt $deadline)
    }

    $processNames = @($PackageName, "$PackageName`:litert", "$PackageName`:python")
    $safeLog = New-Object System.Collections.Generic.List[string]
    foreach ($processName in $processNames) {
        $pidText = Get-AdbValue -Name "log-pid-$processName" -Arguments @('-s', $script:Serial, 'shell', 'pidof', $processName) -AllowNonZero
        foreach ($pidValue in ($pidText -split '\s+' | Where-Object { $_ -match '^\d+$' })) {
            $rawLog = Invoke-Adb -Arguments @('-s', $script:Serial, 'logcat', '-d', '-v', 'threadtime', '--pid', $pidValue) -Name "logcat-$processName" -AllowNonZero
            foreach ($line in (Get-SafeAppLogLines -RawLog $rawLog.Stdout)) { $safeLog.Add($line) }
        }
    }

    $memInfo = Invoke-Adb -Arguments @('-s', $script:Serial, 'shell', 'dumpsys', 'meminfo', $PackageName) -Name 'memory-pss'
    $memSummary = @($memInfo.Stdout -split "`r?`n" | Where-Object {
        $_ -match '^\*\* MEMINFO|^\s*(TOTAL PSS|TOTAL RSS|Native Heap|Dalvik Heap|Java Heap|Graphics|Private Other|System):|^Applications Memory Usage'
    })
    if ($memSummary.Count -eq 0) { $memSummary = @('PSS_SUMMARY_NOT_RECOGNIZED; inspect device-local dumpsys output manually.') }
    Write-Utf8File -Path (Join-Path $script:OutputRoot 'memory-pss.txt') -Text (($memSummary -join "`n") + "`n")

    $thermal = Invoke-Adb -Arguments @('-s', $script:Serial, 'shell', 'dumpsys', 'thermalservice') -Name 'thermal-summary' -AllowNonZero
    $thermalSummary = @($thermal.Stdout -split "`r?`n" | Where-Object { $_ -match '(?i)thermal status|current thermal|temperature|status:' } | Select-Object -First 40)
    if ($thermalSummary.Count -eq 0) { $thermalSummary = @('THERMAL_STATUS_UNAVAILABLE') }
    Write-Utf8File -Path (Join-Path $script:OutputRoot 'thermal-summary.txt') -Text (($thermalSummary -join "`n") + "`n")

    Write-Utf8File -Path (Join-Path $script:OutputRoot 'logcat-redacted.txt') -Text (($safeLog -join "`n") + "`n")
    Write-Utf8File -Path (Join-Path $script:OutputRoot 'commands.json') -Text (($script:CommandRecords | ConvertTo-Json -Depth 5) + "`n")
    Write-Utf8File -Path (Join-Path $script:OutputRoot 'permissions.json') -Text (([ordered]@{
        package = $PackageName
        versionCode = $versionCode
        versionName = $versionName
        requestedPermissionLines = $requestedLines
        checks = $permissionResults
    } | ConvertTo-Json -Depth 5) + "`n")

    $privateCapture = $false
    if ($CaptureConversationEvidence) {
        Write-Warning 'La captura contiene la pantalla completa y podría incluir datos personales. Se guardará solo localmente fuera del repositorio; no la compartas sin revisarla.'
        [void](Read-Host 'Realiza manualmente en el teléfono la conversación que deseas capturar y pulsa Enter cuando la pantalla esté lista')
        $privateDirectory = Join-Path $script:OutputRoot 'private-evidence'
        [void][System.IO.Directory]::CreateDirectory($privateDirectory)
        Save-ConversationScreenshot -Path (Join-Path $privateDirectory 'manual-conversation.png')
        $script:Effects.Add('Explicit user-requested local screenshot capture')
        Write-Utf8File -Path (Join-Path $privateDirectory 'DO_NOT_SHARE_UNREVIEWED.txt') -Text "Captura manual de pantalla completa. Puede contener conversación, documentos u otros datos personales. Revísala y elimina/redacta localmente antes de compartir.`n"
        $privateCapture = $true
    }

    $summary = [ordered]@{
        status = 'PASS_READ_ONLY_SNAPSHOT'
        createdAtUtc = [DateTime]::UtcNow.ToString('o')
        package = $PackageName
        packageInstalled = $true
        versionCode = $versionCode
        versionName = $versionName
        device = $deviceInfo
        processState = $processState
        requestedPermissionLines = $requestedLines
        permissionChecks = $permissionResults
        openAppRequested = [bool]$OpenApp
        conversationScreenshotCaptured = $privateCapture
        conversationScreenshotNotice = if ($privateCapture) { 'Private local file; excluded from shareable checksum manifest.' } else { 'NOT_CAPTURED' }
        effectsPerformed = @($script:Effects)
        restrictions = @('No install, uninstall, clear-data, permission changes, messages, alarms, calendar writes, or model downloads.')
    }
    Write-Utf8File -Path (Join-Path $script:OutputRoot 'summary.json') -Text (($summary | ConvertTo-Json -Depth 6) + "`n")
    Write-Manifest
    Write-Host "Smoke evidence written outside the repository. Device serial recorded as $(Get-MaskedSerial)."
} catch {
    $script:Failure = $_.Exception.Message
    $safeFailure = $script:Failure
    if ($script:Serial) { $safeFailure = $safeFailure.Replace($script:Serial, '<serial-redacted>') }
    Write-Utf8File -Path (Join-Path $script:OutputRoot 'error.txt') -Text ($safeFailure + "`n")
    Write-Utf8File -Path (Join-Path $script:OutputRoot 'commands.json') -Text (($script:CommandRecords | ConvertTo-Json -Depth 5) + "`n")
    $summary = [ordered]@{
        status = 'BLOCKED_OR_FAILED'
        createdAtUtc = [DateTime]::UtcNow.ToString('o')
        package = $PackageName
        deviceSerial = Get-MaskedSerial
        error = $safeFailure
        effectsPerformed = @($script:Effects)
        restrictions = @('No install, uninstall, clear-data, permission changes, messages, alarms, calendar writes, or model downloads.')
    }
    Write-Utf8File -Path (Join-Path $script:OutputRoot 'summary.json') -Text (($summary | ConvertTo-Json -Depth 5) + "`n")
    Write-Manifest
    Write-Error "Smoke check failed; evidence retained at $($script:OutputRoot): $safeFailure"
    exit 1
}
