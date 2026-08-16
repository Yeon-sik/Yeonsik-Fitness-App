param(
    [string]$DeviceSerial = $env:ANDROID_SERIAL,
    [switch]$SkipFinalLaunch
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envPath = Join-Path $repoRoot 'supabase/.env'
$apkPath = Join-Path $repoRoot 'app/build/outputs/apk/debug/app-debug.apk'
$packageName = 'com.yeonsik.fitnessapp'
$activityName = "$packageName/.MainActivity"
$provisionAction = "$packageName.DEBUG_PROVISION_SESSION"

if (-not (Test-Path -LiteralPath $envPath)) {
    throw "Missing supabase/.env"
}

$envValues = @{}
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

foreach ($requiredName in @(
    'SUPABASE_URL',
    'SUPABASE_ANON_KEY',
    'NUTRITION_SUPABASE_URL',
    'NUTRITION_SUPABASE_ANON_KEY',
    'EMAIL',
    'PASSWORD'
)) {
    if (-not $envValues.ContainsKey($requiredName) -or [string]::IsNullOrWhiteSpace($envValues[$requiredName])) {
        throw "supabase/.env is missing $requiredName"
    }
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
    & .\gradlew.bat assembleDebug --no-daemon
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $apkPath)) {
        throw 'Debug APK build failed'
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

    $session = Get-SupabasePasswordSession `
        -BaseUrl $envValues['SUPABASE_URL'] `
        -AnonKey $envValues['SUPABASE_ANON_KEY'] `
        -Email $envValues['EMAIL'] `
        -Password $envValues['PASSWORD']
    $nutritionSession = Get-SupabasePasswordSession `
        -BaseUrl $envValues['NUTRITION_SUPABASE_URL'] `
        -AnonKey $envValues['NUTRITION_SUPABASE_ANON_KEY'] `
        -Email $envValues['EMAIL'] `
        -Password $envValues['PASSWORD']

    $userId = if ($null -ne $session.user.id) { [string]$session.user.id } else { '' }
    $email = if ($null -ne $session.user.email) { [string]$session.user.email } else { $envValues['EMAIL'] }
    $nutritionUserId = if ($null -ne $nutritionSession.user.id) { [string]$nutritionSession.user.id } else { '' }
    $nutritionEmail = if ($null -ne $nutritionSession.user.email) { [string]$nutritionSession.user.email } else { $envValues['EMAIL'] }
    $sessionComplete = -not (
        [string]::IsNullOrWhiteSpace($session.access_token) -or
        [string]::IsNullOrWhiteSpace($session.refresh_token) -or
        [string]::IsNullOrWhiteSpace($userId) -or
        [string]::IsNullOrWhiteSpace($nutritionSession.access_token) -or
        [string]::IsNullOrWhiteSpace($nutritionSession.refresh_token) -or
        [string]::IsNullOrWhiteSpace($nutritionUserId)
    )
    if (-not $sessionComplete) {
        throw 'Supabase password authentication returned an incomplete session'
    }

    & adb -s $DeviceSerial shell am start -n $activityName -a $provisionAction `
        --es access_token $session.access_token `
        --es refresh_token $session.refresh_token `
        --es user_id $userId `
        --es email $email `
        --es nutrition_access_token $nutritionSession.access_token `
        --es nutrition_refresh_token $nutritionSession.refresh_token `
        --es nutrition_user_id $nutritionUserId `
        --es nutrition_email $nutritionEmail | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Session provisioning launch failed'
    }

    Wait-PersistedSession `
        -Device $DeviceSerial `
        -ConfigFile 'fitnessapp:supabase-config:v1.xml' `
        -TokenFile 'fitnessapp:secure-session:v1.xml' `
        -Label 'Shared Supabase'
    Wait-PersistedSession `
        -Device $DeviceSerial `
        -ConfigFile 'fitnessapp:nutrition-supabase-config:v1.xml' `
        -TokenFile 'fitnessapp:secure-nutrition-session:v1.xml' `
        -Label 'Nutrition Supabase'

    if (-not $SkipFinalLaunch) {
        & adb -s $DeviceSerial shell am start -n $activityName | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw 'Final app launch failed'
        }
        Wait-PersistedSession `
            -Device $DeviceSerial `
            -ConfigFile 'fitnessapp:supabase-config:v1.xml' `
            -TokenFile 'fitnessapp:secure-session:v1.xml' `
            -Label 'Shared Supabase after launch'
        Wait-PersistedSession `
            -Device $DeviceSerial `
            -ConfigFile 'fitnessapp:nutrition-supabase-config:v1.xml' `
            -TokenFile 'fitnessapp:secure-nutrition-session:v1.xml' `
            -Label 'Nutrition Supabase after launch'
    }
    Write-Host "Updated $packageName on $DeviceSerial with adb install -r; existing app data was preserved."
} finally {
    Pop-Location
}
