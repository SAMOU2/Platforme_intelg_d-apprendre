# PC1 uniquement. Ne supprime jamais les conteneurs/volumes et ne rejoue aucune migration.
param([switch]$PourAuthentification)
$ErrorActionPreference = 'Stop'
function Test-MoteurDocker([string]$Executable) {
    # Sous Windows PowerShell 5, stderr natif peut devenir une erreur PowerShell.
    $anciennePreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $Executable info --format '{{.ServerVersion}}' *> $null
        return ($LASTEXITCODE -eq 0)
    } finally { $ErrorActionPreference = $anciennePreference }
}
try {
    Set-Location (Split-Path $PSScriptRoot -Parent)
    if ($PourAuthentification) {
        if ($env:IHM_DB_DEMARRAGE_AUTO -eq '0') {
            Write-Host '[INFO] Demarrage Docker automatique desactive : base geree manuellement.'
            exit 0
        }
        $portLocal = if ($env:IHM_DB_PORT) { $env:IHM_DB_PORT } else { '5433' }
        # Ne pas lancer une autre base lorsqu'une base externe/personnalisee est configuree.
        if ($env:IHM_DB_URL -notmatch '^jdbc:postgresql://(?:localhost|127\.0\.0\.1|\[::1\]):([0-9]+)/' -or $Matches[1] -ne $portLocal) {
            Write-Host '[INFO] Base externe ou port personnalise : demarrage laisse a votre configuration.'
            exit 0
        }
    }
    $docker = Get-Command docker.exe -ErrorAction SilentlyContinue
    if (-not $docker) {
        $cli = Join-Path $env:ProgramFiles 'Docker\Docker\resources\bin\docker.exe'
        if (Test-Path $cli) { $docker = Get-Item $cli }
        else { throw 'Docker Desktop introuvable. Installez-le sur PC1 et terminez sa configuration initiale.' }
    }
    $dockerExe = if ($docker -is [System.IO.FileInfo]) { $docker.FullName } else { $docker.Source }
    if (-not (Test-MoteurDocker $dockerExe)) {
        $candidats = @(
            (Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'),
            (Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\Docker Desktop.exe')
        )
        $desktop = $candidats | Where-Object { Test-Path $_ } | Select-Object -First 1
        if (-not $desktop) { throw 'Moteur Docker indisponible. Ouvrez Docker Desktop manuellement puis reessayez.' }
        Write-Host '[INFO] Ouverture de Docker Desktop...'
        Start-Process -FilePath $desktop
        $attente = [Diagnostics.Stopwatch]::StartNew()
        do {
            Start-Sleep -Seconds 2
            if (Test-MoteurDocker $dockerExe) { break }
            if ($attente.Elapsed.TotalSeconds -ge 120) {
                throw 'Docker ne repond pas apres 120 secondes. Verifiez Docker Desktop, WSL 2 et les eventuelles demandes de confirmation, puis relancez.'
            }
        } while ($true)
    }
    & $dockerExe compose version
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose v2 est requis. Mettez Docker Desktop a jour.' }
    Write-Host '[INFO] Demarrage de PostgreSQL et attente de son etat healthy...'
    & $dockerExe compose -f db/docker-compose.yml up -d --wait --wait-timeout 120 postgres
    if ($LASTEXITCODE -ne 0) {
        throw 'PostgreSQL non pret. Verifiez Docker Desktop et docker compose -f db/docker-compose.yml logs --tail 50 postgres. Si --wait est inconnu, mettez Compose a jour. Ne supprimez aucun volume.'
    }
    Write-Host '[OK] PostgreSQL est pret dans Docker. Les donnees existantes sont conservees.'
    Write-Host '[INFO] Les migrations manquantes sur une base existante restent a appliquer manuellement apres sauvegarde.'
    exit 0
} catch {
    Write-Host ('[ERREUR] ' + $_.Exception.Message) -ForegroundColor Red
    exit 1
}
