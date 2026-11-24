# PC1 uniquement. Applique les migrations db/*.sql absentes d'un volume PostgreSQL deja cree.
# Aucune suppression : ni table, ni donnee, ni volume. Chaque script est idempotent.
param(
    [switch]$AvecDemonstration,
    [switch]$Simulation,
    [switch]$Auto
)
# Code de sortie propre au mode -Auto (utilise au demarrage du service) :
#   0 = base a jour (ou mise a jour appliquee), 1 = echec, 2 = base non joignable
#   pour le moment (le service demarre quand meme, verification au prochain demarrage).
$CodeBaseInjoignable = 2
$ErrorActionPreference = 'Stop'

function Test-MoteurDocker([string]$Executable) {
    $anciennePreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $Executable info --format '{{.ServerVersion}}' *> $null
        return ($LASTEXITCODE -eq 0)
    } finally { $ErrorActionPreference = $anciennePreference }
}

try {
    Set-Location (Split-Path $PSScriptRoot -Parent)

    # Configuration locale : jamais affichee, seulement chargee.
    $configuration = Join-Path $PSScriptRoot 'configuration-PC1.local.cmd'
    if (Test-Path $configuration) {
        Write-Host '[INFO] Configuration locale chargee (secrets non affiches).'
        & cmd.exe /c "call `"$configuration`" >nul 2>nul && set" | ForEach-Object {
            if ($_ -match '^([^=]+)=(.*)$') { Set-Item -Path ("env:" + $matches[1]) -Value $matches[2] }
        }
    }

    $docker = Get-Command docker.exe -ErrorAction SilentlyContinue
    if (-not $docker) {
        $cli = Join-Path $env:ProgramFiles 'Docker\Docker\resources\bin\docker.exe'
        if (Test-Path $cli) { $docker = Get-Item $cli }
    }
    if (-not $docker) {
        if ($Auto) {
            Write-Host '[ATTENTION] Docker Desktop introuvable : migrations non verifiees.'
            exit $CodeBaseInjoignable
        }
        throw 'Docker Desktop introuvable : les migrations ne peuvent pas etre appliquees depuis ce poste.'
    }
    $dockerExe = if ($docker -is [System.IO.FileInfo]) { $docker.FullName } else { $docker.Source }
    if (-not (Test-MoteurDocker $dockerExe)) {
        if ($Auto) {
            Write-Host '[ATTENTION] Moteur Docker arrete : migrations non verifiees, base non modifiee.'
            exit $CodeBaseInjoignable
        }
        throw 'Moteur Docker arrete. Ouvrir Docker Desktop, attendre la fin du demarrage, puis relancer ce script.'
    }

    $conteneur = 'ihm2026-postgres'
    $utilisateur = if ($env:IHM_DB_USER) { $env:IHM_DB_USER } else { 'ihm2026' }
    $base = if ($env:IHM_DB_NAME) { $env:IHM_DB_NAME } else { 'ihm2026' }

    & $dockerExe inspect --format '{{.State.Running}}' $conteneur *> $null
    if ($LASTEXITCODE -ne 0) {
        if ($Auto) {
            Write-Host "[ATTENTION] Conteneur $conteneur absent : migrations non verifiees."
            exit $CodeBaseInjoignable
        }
        throw "Conteneur $conteneur absent. Demarrer la base avec postes\demarrer-docker-PC1.bat, puis relancer."
    }

    # Etat des objets qui prouvent qu'une migration est appliquee.
    if (-not $Auto) { Write-Host '[INFO] Verification de l''etat de la base (lecture seule)...' }
    $verifications = [ordered]@{
        '10-correction-enseignant.sql'        = 'SELECT to_regclass(''public.affectations_pedagogiques'') IS NOT NULL;'
        '11-contenus-niveaux.sql'             = 'SELECT to_regclass(''public.entrainements_niveaux'') IS NOT NULL;'
        '12-parcours-enseignants.sql'         = 'SELECT to_regclass(''public.parcours_enseignants'') IS NOT NULL;'
        '13-scalabilite-comptes.sql'          = 'SELECT to_regclass(''public.ix_utilisateurs_admin_statut_date'') IS NOT NULL;'
        '14-appareils-confiance-48h.sql'      = 'SELECT to_regclass(''public.appareils_confiance'') IS NOT NULL;'
        '15-reinitialisation-mot-de-passe.sql'= 'SELECT to_regclass(''public.reinitialisations_mot_de_passe'') IS NOT NULL;'
        '16-offres-et-demandes-inscription.sql' = 'SELECT to_regclass(''public.offres_enseignants'') IS NOT NULL AND to_regclass(''public.demandes_inscription'') IS NOT NULL;'
        '17-offres-demonstration.sql'         = 'SELECT EXISTS(SELECT 1 FROM offres_enseignants) OR to_regclass(''public.offres_enseignants'') IS NULL;'
    }

    $manquantes = @()
    foreach ($migration in $verifications.Keys) {
        $sortie = & $dockerExe exec $conteneur psql -tA -U $utilisateur -d $base -c $verifications[$migration] 2>&1
        if ($LASTEXITCODE -ne 0) {
            if ($Auto) {
                Write-Host '[ATTENTION] Lecture de la base impossible pour le moment : migrations non verifiees.'
                exit $CodeBaseInjoignable
            }
            throw "Lecture de la base impossible. Verifier IHM_DB_USER/IHM_DB_NAME puis : docker logs --tail 50 $conteneur"
        }
        $etat = ($sortie | Out-String).Trim()
        if ($etat -eq 't') {
            if (-not $Auto) { Write-Host ("  [OK]     " + $migration) }
        } else {
            if (-not ($Auto -and $migration -eq '17-offres-demonstration.sql')) {
                if (-not $Auto) { Write-Host ("  [ABSENT] " + $migration) -ForegroundColor Yellow }
                else { Write-Host ("[INFO] Migration absente : " + $migration) }
            }
            if ($migration -ne '17-offres-demonstration.sql' -or $AvecDemonstration) { $manquantes += $migration }
        }
    }

    if ($manquantes.Count -eq 0) {
        if (-not $Auto) { Write-Host '[OK] Toutes les migrations attendues sont deja appliquees : rien a faire.' }
        exit 0
    }

    Write-Host ''
    if ($Auto) {
        Write-Host ('[INFO] Mise a niveau de la base : ' + ($manquantes -join ', '))
    } else {
        Write-Host ('Migrations a appliquer : ' + ($manquantes -join ', '))
    }
    if ($Simulation) {
        Write-Host '[SIMULATION] Aucune modification effectuee. Relancer sans -Simulation pour appliquer.'
        exit 0
    }

    # Sauvegarde de securite avant toute ecriture.
    $dossier = Join-Path (Get-Location) 'sauvegardes'
    New-Item -ItemType Directory -Force -Path $dossier | Out-Null
    $fichier = Join-Path $dossier ('avant-migrations-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.sql')
    if (-not $Auto) { Write-Host ('[INFO] Sauvegarde de la base : ' + $fichier) }
    & $dockerExe exec $conteneur pg_dump -U $utilisateur -d $base | Set-Content -Encoding UTF8 -Path $fichier
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $fichier) -or (Get-Item $fichier).Length -eq 0) {
        throw 'Sauvegarde impossible ou vide : aucune migration appliquee. Verifier les droits du dossier sauvegardes\.'
    }

    foreach ($migration in $manquantes) {
        $script = Join-Path (Get-Location) ('db\' + $migration)
        if (-not (Test-Path $script)) { throw ("Fichier introuvable : " + $script + ". Mettre le projet a jour (git pull).") }
        if (-not $Auto) { Write-Host ('[INFO] Application de ' + $migration + ' ...') }
        Get-Content -Raw -Encoding UTF8 $script | & $dockerExe exec -i $conteneur psql -v ON_ERROR_STOP=1 -U $utilisateur -d $base *> $null
        if ($LASTEXITCODE -ne 0) {
            throw ("Echec sur " + $migration + ". La base est inchangee pour cette migration ; restaurer la sauvegarde si besoin : " + $fichier)
        }
        Write-Host ('  [OK]     ' + $migration)
    }

    # Verification finale : relecture des memes indicateurs.
    $restantes = @()
    foreach ($migration in $manquantes) {
        if ($migration -eq '17-offres-demonstration.sql') { continue }
        $sortie = & $dockerExe exec $conteneur psql -tA -U $utilisateur -d $base -c $verifications[$migration] 2>&1
        if (($sortie | Out-String).Trim() -ne 't') { $restantes += $migration }
    }
    if ($restantes.Count -gt 0) {
        throw ('Migrations toujours absentes apres application : ' + ($restantes -join ', '))
    }

    Write-Host ''
    if ($Auto) {
        Write-Host '[OK] Base mise a niveau.'
    } else {
        Write-Host '[OK] Base a jour. Redemarrer le service d''authentification : postes\authentification-PC1.bat'
        Write-Host ('[INFO] Sauvegarde conservee : ' + $fichier)
    }
    exit 0
} catch {
    Write-Host ('[ERREUR] ' + $_.Exception.Message) -ForegroundColor Red
    exit 1
}
