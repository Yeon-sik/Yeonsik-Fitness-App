param(
    [string]$DeviceSerial = $env:ANDROID_SERIAL,
    [switch]$SkipFinalLaunch,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envPath = Join-Path $repoRoot 'supabase/.env'
$apkPath = Join-Path $repoRoot 'app/build/outputs/apk/debug/app-debug.apk'
$packageName = 'com.yeonsik.fitnessapp'
$activityName = "$packageName/.MainActivity"
$provisionAction = "$packageName.DEBUG_PROVISION_SESSION"

$envValues = @{}
if (Test-Path -LiteralPath $envPath) {
    foreach ($line in Get-Content -LiteralPath $envPath) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') {
            $name = $Matches[1]
            $value = $Matches[2].Trim()
            if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            $envValues[$name] = $value
        }
    }
}

function Get-EnvValue {
    param([string[]]$Names)
    foreach ($name in $Names) {
        if ($envValues.ContainsKey($name) -and -not [string]::IsNullOrWhiteSpace($envValues[$name])) {
            return [string]$envValues[$name]
        }
    }
    return ''
}

function Get-SupabasePasswordSession {
    param(
        [string]$BaseUrl,
        [string]$AnonKey,
        [string]$Email,
        [string]$Password
    )

    $authUri = $BaseUrl.TrimEnd('/') + '/auth/v1/token?grant_type=password'
    $authHeaders = @{ apikey = $AnonKey }
    $authBody = @{
        email    = $Email
        password = $Password
    } | ConvertTo-Json -Compress
    return Invoke-RestMethod -Method Post -Uri $authUri -Headers $authHeaders -ContentType 'application/json' -Body $authBody
}

function Read-AppPrivateFile {
    param(
        [string]$Device,
        [string]$RelativePath
    )
    return ((& adb -s $Device shell run-as $packageName cat "shared_prefs/$RelativePath" 2>$null) | Out-String)
}

function Test-PersistedSession {
    param(
        [string]$Device,
        [string]$ConfigFile,
        [string]$TokenFile
    )
    $configXml = Read-AppPrivateFile -Device $Device -RelativePath $ConfigFile
    $tokenXml = Read-AppPrivateFile -Device $Device -RelativePath $TokenFile
    return $configXml -match '<string name="user_id">[^<]+</string>' `
        -and $configXml -match '<string name="email">[^<]+</string>' `
        -and $tokenXml -match '<string name="access_token">[^<]+</string>' `
        -and $tokenXml -match '<string name="refresh_token">[^<]+</string>'
}

function Wait-PersistedSession {
    param(
        [string]$Device,
        [string]$ConfigFile,
        [string]$TokenFile,
        [string]$Label
    )
    $deadline = [DateTime]::UtcNow.AddSeconds(10)
    do {
        if (Test-PersistedSession -Device $Device -ConfigFile $ConfigFile -TokenFile $TokenFile) {
            return
        }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "$Label session was not persisted on the device; refusing to report auto-login success"
}

Push-Location $repoRoot
try {
    if (-not $SkipBuild) {
        & .\gradlew.bat assembleDebug --no-daemon
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $apkPath)) {
            throw 'Debug APK build failed'
        }
    } elseif (-not (Test-Path -LiteralPath $apkPath)) {
        throw 'Debug APK was not found; run assembleDebug first'
    }

    $deviceLines = @(& adb devices)
    $readyDevices = @($deviceLines | Where-Object { $_ -match '^([^\s]+)\s+device\s*$' } | ForEach-Object { $Matches[1] })
    if ([string]::IsNullOrWhiteSpace($DeviceSerial)) {
        if ($readyDevices.Count -ne 1) {
            throw "Expected exactly one authorized adb device; found $($readyDevices.Count)"
        }
        $DeviceSerial = $readyDevices[0]
    } elseif ($readyDevices -notcontains $DeviceSerial) {
        throw "Requested adb device is not in ready state: $DeviceSerial"
    }

    & adb -s $DeviceSerial install -r $apkPath
    if ($LASTEXITCODE -ne 0) {
        throw 'APK installation failed'
    }

    # Ensure the provisioning intent is handled by a fresh debug Activity.
    # This stops the process only; it does not clear app data or uninstall the APK.
    & adb -s $DeviceSerial shell am force-stop $packageName
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not restart app process for session provisioning'
    }

    $sessionArgs = @('shell', 'am', 'start', '-n', $activityName, '-a', $provisionAction)
    $persistedChecks = @()
    $autoLoginLabels = @()

    $sharedUrl = Get-EnvValue @('SUPABASE_URL', 'VITE_SUPABASE_URL', 'ORIGINAL_DB_URL')
    $sharedAnonKey = Get-EnvValue @('SUPABASE_ANON_KEY', 'VITE_SUPABASE_ANON_KEY', 'ORIGINAL_DB_ANON')
    $email = Get-EnvValue @('EMAIL')
    $password = Get-EnvValue @('PASSWORD')
    if ($sharedUrl -and $sharedAnonKey -and $email -and $password) {
        try {
            $session = Get-SupabasePasswordSession `
                -BaseUrl $sharedUrl `
                -AnonKey $sharedAnonKey `
                -Email $email `
                -Password $password
            $userId = if ($null -ne $session.user.id) { [string]$session.user.id } else { '' }
            $sessionEmail = if ($null -ne $session.user.email) { [string]$session.user.email } else { $email }
            if ([string]::IsNullOrWhiteSpace($session.access_token) `
                    -or [string]::IsNullOrWhiteSpace($session.refresh_token) `
                    -or [string]::IsNullOrWhiteSpace($userId)) {
                throw 'incomplete session'
            }
            $sessionArgs += @(
                '--es', 'access_token', $session.access_token,
                '--es', 'refresh_token', $session.refresh_token,
                '--es', 'user_id', $userId,
                '--es', 'email', $sessionEmail
            )
            $persistedChecks += [pscustomobject]@{
                ConfigFile = 'fitnessapp:supabase-config:v1.xml'
                TokenFile = 'fitnessapp:secure-session:v1.xml'
                Label = 'Shared Supabase'
            }
            $autoLoginLabels += 'Personal OS'
        } catch {
            Write-Warning 'Personal OS 자동 로그인을 건너뜁니다. 앱 설정에서 직접 로그인하세요.'
        }
    }

    $nutritionUrl = Get-EnvValue @('NUTRITION_SUPABASE_URL', 'NUTRITION_DB_URL')
    $nutritionAnonKey = Get-EnvValue @(
        'NUTRITION_SUPABASE_ANON_KEY',
        'NUTRITION_DB_ANON_KEY',
        'NUTRITION_DB_ANON'
    )
    if ($nutritionUrl -and $nutritionAnonKey -and $email -and $password) {
        try {
            $nutritionSession = Get-SupabasePasswordSession `
                -BaseUrl $nutritionUrl `
                -AnonKey $nutritionAnonKey `
                -Email $email `
                -Password $password
            $nutritionUserId = if ($null -ne $nutritionSession.user.id) { [string]$nutritionSession.user.id } else { '' }
            $nutritionEmail = if ($null -ne $nutritionSession.user.email) { [string]$nutritionSession.user.email } else { $email }
            if ([string]::IsNullOrWhiteSpace($nutritionSession.access_token) `
                    -or [string]::IsNullOrWhiteSpace($nutritionSession.refresh_token) `
                    -or [string]::IsNullOrWhiteSpace($nutritionUserId)) {
                throw 'incomplete session'
            }
            $sessionArgs += @(
                '--es', 'nutrition_access_token', $nutritionSession.access_token,
                '--es', 'nutrition_refresh_token', $nutritionSession.refresh_token,
                '--es', 'nutrition_user_id', $nutritionUserId,
                '--es', 'nutrition_email', $nutritionEmail
            )
            $persistedChecks += [pscustomobject]@{
                ConfigFile = 'fitnessapp:nutrition-supabase-config:v1.xml'
                TokenFile = 'fitnessapp:secure-nutrition-session:v1.xml'
                Label = 'Nutrition Supabase'
            }
            $autoLoginLabels += 'Nutrition'
        } catch {
            Write-Warning 'Nutrition 자동 로그인을 건너뜁니다. 앱 설정에서 직접 로그인하세요.'
        }
    }

    $priceTraceUrl = Get-EnvValue @('PRICETRACE_SUPABASE_URL', 'PRICETRACE_DB_URL')
    $priceTraceAnonKey = Get-EnvValue @(
        'PRICETRACE_SUPABASE_ANON_KEY',
        'PRICETRACE_SUPABASE_ANON',
        'PRICETRACE_DB_ANON_KEY',
        'PRICETRACE_DB_ANON'
    )
    $priceTraceEmail = Get-EnvValue @('PRICE_TRACE_EMAIL')
    $priceTracePassword = Get-EnvValue @('PRICE_TRACE_PW')
    if ($priceTraceUrl -and $priceTraceAnonKey -and $priceTraceEmail -and $priceTracePassword) {
        try {
            $priceTraceSession = Get-SupabasePasswordSession `
                -BaseUrl $priceTraceUrl `
                -AnonKey $priceTraceAnonKey `
                -Email $priceTraceEmail `
                -Password $priceTracePassword
            $priceTraceUserId = if ($null -ne $priceTraceSession.user.id) { [string]$priceTraceSession.user.id } else { '' }
            $resolvedPriceTraceEmail = if ($null -ne $priceTraceSession.user.email) {
                [string]$priceTraceSession.user.email
            } else {
                $priceTraceEmail
            }
            if ([string]::IsNullOrWhiteSpace($priceTraceSession.access_token) `
                    -or [string]::IsNullOrWhiteSpace($priceTraceSession.refresh_token) `
                    -or [string]::IsNullOrWhiteSpace($priceTraceUserId)) {
                throw 'incomplete session'
            }
            $sessionArgs += @(
                '--es', 'price_trace_access_token', $priceTraceSession.access_token,
                '--es', 'price_trace_refresh_token', $priceTraceSession.refresh_token,
                '--es', 'price_trace_user_id', $priceTraceUserId,
                '--es', 'price_trace_email', $resolvedPriceTraceEmail
            )
            $persistedChecks += [pscustomobject]@{
                ConfigFile = 'fitnessapp:pricetrace-supabase-config:v1.xml'
                TokenFile = 'fitnessapp:secure-pricetrace-session:v1.xml'
                Label = 'PriceTrace Supabase'
            }
            $autoLoginLabels += 'PriceTrace'
        } catch {
            Write-Warning 'PriceTrace 자동 로그인을 건너뜁니다. 앱 설정에서 직접 로그인하세요.'
        }
    }

    if ($autoLoginLabels.Count -gt 0) {
        & adb -s $DeviceSerial @sessionArgs | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw 'Session provisioning launch failed'
        }
        foreach ($check in $persistedChecks) {
            Wait-PersistedSession `
                -Device $DeviceSerial `
                -ConfigFile $check.ConfigFile `
                -TokenFile $check.TokenFile `
                -Label $check.Label
        }
    } else {
        Write-Host '자동 로그인 정보가 없어 앱 설정 화면에서 로그인할 수 있습니다.'
    }

    if (-not $SkipFinalLaunch) {
        & adb -s $DeviceSerial shell am start -n $activityName | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw 'Final app launch failed'
        }
        foreach ($check in $persistedChecks) {
            Wait-PersistedSession `
                -Device $DeviceSerial `
                -ConfigFile $check.ConfigFile `
                -TokenFile $check.TokenFile `
                -Label "$($check.Label) after launch"
        }
    }
    if ($autoLoginLabels.Count -gt 0) {
        Write-Host "Updated $packageName on $DeviceSerial with adb install -r and provisioned: $($autoLoginLabels -join ', ')."
    } else {
        Write-Host "Updated $packageName on $DeviceSerial with adb install -r; use the in-app login form."
    }
} finally {
    Pop-Location
}
