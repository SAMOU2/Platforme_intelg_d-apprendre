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

# Appel d'une commande native en capturant sa sortie standard ET sa sortie d'erreur.
# Indispensable : avec $ErrorActionPreference = 'Stop', PowerShell 5 transforme un
# message ecrit sur la sortie d'erreur de psql (par exemple « relation ... does not
# exist ») en erreur bloquante. Le script s'arretait alors sur la premiere sonde
# impossible avant meme d'appliquer la moindre migration.
function Invoke-CommandeNative([string]$Executable, [string[]]$Arguments) {
    $anciennePreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $lignes = & $Executable @Arguments 2>&1
        $code = $LASTEXITCODE
        return [pscustomobject]@{
            Code   = $code
            Sortie = ((@($lignes) | ForEach-Object { "$_" }) -join [Environment]::NewLine).Trim()
        }
    } finally { $ErrorActionPreference = $anciennePreference }
}

# Execute un fichier SQL dans le conteneur : psql relit l'entree standard.
function Invoke-PsqlFichier([string]$Executable, [string]$Conteneur, [string]$Utilisateur,
        [string]$Base, [string]$Fichier) {
    $anciennePreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $contenu = Get-Content -Raw -Encoding UTF8 $Fichier
        $lignes = $contenu | & $Executable exec -i $Conteneur psql -v ON_ERROR_STOP=1 -U $Utilisateur -d $Base 2>&1
        $code = $LASTEXITCODE
        return [pscustomobject]@{
            Code   = $code
            Sortie = ((@($lignes) | ForEach-Object { "$_" }) -join [Environment]::NewLine).Trim()
        }
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

    $inspection = Invoke-CommandeNative $dockerExe @('inspect', '--format', '{{.State.Running}}', $conteneur)
    if ($inspection.Code -ne 0) {
        if ($Auto) {
            Write-Host "[ATTENTION] Conteneur $conteneur absent : migrations non verifiees."
            exit $CodeBaseInjoignable
        }
        throw "Conteneur $conteneur absent. Demarrer la base avec postes\demarrer-docker-PC1.bat, puis relancer."
    }

    # Premiere question, la seule qui distingue « base injoignable » d'une sonde impossible :
    # la base repond-elle ? Les sondes suivantes peuvent echouer sur une table absente, ce
    # qui est normal : cela signifie simplement que la migration n'est pas encore appliquee.
    $connexion = Invoke-CommandeNative $dockerExe @('exec', $conteneur, 'psql', '-tA', '-U', $utilisateur, '-d', $base, '-c', 'SELECT 1;')
    if ($connexion.Code -ne 0) {
        $detail = if ($connexion.Sortie) { ' Detail : ' + $connexion.Sortie } else { '' }
        if ($Auto) {
            Write-Host '[ATTENTION] Base non joignable pour le moment : migrations non verifiees.'
            exit $CodeBaseInjoignable
        }
        throw ("Lecture de la base impossible. Verifier que PostgreSQL est demarre, puis IHM_DB_USER/IHM_DB_NAME." +
               $detail + " Diagnostics : docker logs --tail 50 " + $conteneur)
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
        # Cette sonde interroge offres_enseignants : sans la migration 16 elle echoue sur
        # « relation does not exist ». C'est normal et traite comme « rien a publier encore ».
        '17-offres-demonstration.sql'         = 'SELECT EXISTS(SELECT 1 FROM offres_enseignants);'
    }

    $manquantes = @()
    foreach ($migration in $verifications.Keys) {
        $sonde = Invoke-CommandeNative $dockerExe @('exec', $conteneur, 'psql', '-tA', '-U', $utilisateur, '-d', $base, '-c', $verifications[$migration])
        $appliquee = ($sonde.Code -eq 0 -and $sonde.Sortie -eq 't')
        if (-not $appliquee -and $sonde.Code -ne 0) {
            # Une sonde en echec peut aussi signaler une base devenue injoignable : on revérifie.
            $reprise = Invoke-CommandeNative $dockerExe @('exec', $conteneur, 'psql', '-tA', '-U', $utilisateur, '-d', $base, '-c', 'SELECT 1;')
            if ($reprise.Code -ne 0) {
                if ($Auto) {
                    Write-Host '[ATTENTION] Base non joignable en cours de verification : migrations non verifiees.'
                    exit $CodeBaseInjoignable
                }
                throw ('Lecture de la base interrompue pendant la verification de ' + $migration + '. Detail : ' + $sonde.Sortie)
            }
        }
        if ($appliquee) {
            if (-not $Auto) { Write-Host ("  [OK]     " + $migration) }
        } else {
            $estDemonstration = ($migration -eq '17-offres-demonstration.sql')
            if (-not ($Auto -and $estDemonstration)) {
                if (-not $Auto) { Write-Host ("  [ABSENT] " + $migration) -ForegroundColor Yellow }
                else { Write-Host ("[INFO] Migration absente : " + $migration) }
            }
            if (-not $estDemonstration -or $AvecDemonstration) { $manquantes += $migration }
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
    $sauvegarde = Invoke-CommandeNative $dockerExe @('exec', $conteneur, 'pg_dump', '-U', $utilisateur, '-d', $base)
    if ($sauvegarde.Code -eq 0 -and $sauvegarde.Sortie) {
        Set-Content -Encoding UTF8 -Path $fichier -Value $sauvegarde.Sortie
    }
    if ($sauvegarde.Code -ne 0 -or -not (Test-Path $fichier) -or (Get-Item $fichier).Length -eq 0) {
        throw ('Sauvegarde impossible ou vide : aucune migration appliquee. ' +
               'Verifier les droits du dossier sauvegardes\. Detail : ' + $sauvegarde.Sortie)
    }

    foreach ($migration in $manquantes) {
        $script = Join-Path (Get-Location) ('db\' + $migration)
        if (-not (Test-Path $script)) { throw ("Fichier introuvable : " + $script + ". Mettre le projet a jour (git pull).") }
        if (-not $Auto) { Write-Host ('[INFO] Application de ' + $migration + ' ...') }
        $application = Invoke-PsqlFichier $dockerExe $conteneur $utilisateur $base $script
        if ($application.Code -ne 0) {
            # Le message de PostgreSQL est repris tel quel : c'est lui qui dit quoi corriger.
            throw ("Echec sur " + $migration + ". " + $application.Sortie +
                   " La base reste dans son etat precedent ; sauvegarde disponible : " + $fichier)
        }
        Write-Host ('  [OK]     ' + $migration)
    }

    # Verification finale : relecture des memes indicateurs.
    $restantes = @()
    foreach ($migration in $manquantes) {
        if ($migration -eq '17-offres-demonstration.sql') { continue }
        $sonde = Invoke-CommandeNative $dockerExe @('exec', $conteneur, 'psql', '-tA', '-U', $utilisateur, '-d', $base, '-c', $verifications[$migration])
        if ($sonde.Code -ne 0 -or $sonde.Sortie -ne 't') { $restantes += $migration }
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
