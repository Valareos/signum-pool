# Push current branch (or named branch) using credentials from push-auth.local.
# Usage:  .\push.ps1
#         .\push.ps1 feature/farm-submission-webhook
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$AuthFile = Join-Path $Root 'push-auth.local'

if (-not (Test-Path $AuthFile)) {
    Write-Error "Missing push-auth.local - copy push-auth.local.example and set GITHUB_USER + GITHUB_TOKEN."
}

$cfg = @{}
Get-Content $AuthFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq '' -or $line.StartsWith('#')) { return }
    if ($line -match '^([^=]+)=(.*)$') {
        $cfg[$Matches[1].Trim()] = $Matches[2].Trim()
    }
}

$user = $cfg['GITHUB_USER']
$token = $cfg['GITHUB_TOKEN']
$repo = $cfg['GITHUB_REPO']
if (-not $user -or -not $token -or -not $repo) {
    Write-Error 'push-auth.local must define GITHUB_USER, GITHUB_TOKEN, GITHUB_REPO'
}

Push-Location $Root
try {
    $branch = if ($args.Count -gt 0) { $args[0] } else {
        (git rev-parse --abbrev-ref HEAD).Trim()
    }
    if (-not $branch) {
        Write-Error 'Could not determine branch'
    }

    $remote = "https://${user}:${token}@github.com/${repo}.git"
    Write-Host "Pushing $branch to $repo ..."
    & git push $remote "${branch}:${branch}"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    & git fetch origin $branch 2>&1 | Out-Null
    & git branch -u "origin/$branch" $branch 2>&1 | Out-Null
    Write-Host "OK: ${branch} pushed."
}
finally {
    Pop-Location
}
