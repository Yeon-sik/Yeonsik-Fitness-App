param(
    [string]$SourceEnv = "",
    [string]$CashOsEnv = "",
    [switch]$CheckOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$personalOsRoot = Split-Path -Parent $repoRoot

if ([string]::IsNullOrWhiteSpace($SourceEnv)) {
    $SourceEnv = Join-Path $personalOsRoot "PersonalOSApp\.env"
}
if ([string]::IsNullOrWhiteSpace($CashOsEnv)) {
    $CashOsEnv = Join-Path $personalOsRoot "CashOS\.env"
}

function Read-DotEnv([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Supabase environment file was not found: $Path"
    }

    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') {
            $values[$Matches[1]] = $Matches[2].Trim().Trim('"').Trim("'")
        }
    }
    return $values
}

function First-Value([hashtable]$Values, [string[]]$Keys) {
    foreach ($key in $Keys) {
        if ($Values.ContainsKey($key) -and -not [string]::IsNullOrWhiteSpace($Values[$key])) {
            return $Values[$key]
        }
    }
    return ""
}

function Read-SupabaseConnection([string]$Path) {
    $values = Read-DotEnv $Path
    $url = First-Value $values @("SUPABASE_URL", "VITE_SUPABASE_URL", "ORIGINAL_DB_URL")
    $anonKey = First-Value $values @(
        "SUPABASE_ANON_KEY",
        "VITE_SUPABASE_ANON_KEY",
        "ORIGINAL_DB_ANON"
    )

    if ([string]::IsNullOrWhiteSpace($url) -or [string]::IsNullOrWhiteSpace($anonKey)) {
        throw "Supabase URL and anon/publishable key must both be present in: $Path"
    }

    $uri = [Uri]$url
    if ($uri.Scheme -ne "https" -or $uri.Host -notmatch '^([a-z0-9-]+)\.supabase\.co$') {
        throw "Expected an HTTPS Supabase project URL in: $Path"
    }

    return [pscustomobject]@{
        Url = $url
        AnonKey = $anonKey
        ProjectRef = $Matches[1]
    }
}

$sourceConnection = Read-SupabaseConnection $SourceEnv

if (Test-Path -LiteralPath $CashOsEnv) {
    $cashOsConnection = Read-SupabaseConnection $CashOsEnv
    if ($cashOsConnection.ProjectRef -ne $sourceConnection.ProjectRef) {
        throw "CashOS and PersonalOSApp point to different Supabase projects. No FitnessApp config was written."
    }
}

$outputPath = Join-Path $repoRoot "supabase\.env"
if ($CheckOnly) {
    $fitnessConnection = Read-SupabaseConnection $outputPath
    if ($fitnessConnection.ProjectRef -ne $sourceConnection.ProjectRef) {
        throw "FitnessApp does not point to the same Supabase project as PersonalOSApp."
    }
    Write-Output "Shared Supabase configuration matches across apps. project_ref=$($sourceConnection.ProjectRef)"
    exit 0
}

$lines = @(
    "SUPABASE_URL=$($sourceConnection.Url)",
    "SUPABASE_ANON_KEY=$($sourceConnection.AnonKey)"
)
$utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines($outputPath, $lines, $utf8WithoutBom)

Write-Output "FitnessApp shared Supabase config created. project_ref=$($sourceConnection.ProjectRef)"
Write-Output "Output: $outputPath (ignored by Git)"
