# A executer explicitement comme administrateur sur PC1, apres demarrage HTTPS.
# Limite au programme, au port courant, au profil prive et au sous-reseau local.
$ErrorActionPreference = 'Stop'
try {
    $identite = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identite)
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw 'Relancer ce fichier CMD sur PC1 avec clic droit > Executer en tant qu administrateur.'
    }
    $registre = Join-Path (Split-Path $PSScriptRoot -Parent) '.ihm-runtime\https.port'
    if (-not (Test-Path -LiteralPath $registre)) { throw 'Registre absent : demarrer authentification-PC1.bat dans ce dossier projet.' }
    $champs = (Get-Content -LiteralPath $registre -Raw).Trim() -split '\s+'
    $port = 0; $pidServeur = 0; $debut = 0L
    if ($champs.Count -ne 3 -or -not [int]::TryParse($champs[0], [ref]$port) -or
        -not [int]::TryParse($champs[1], [ref]$pidServeur) -or
        -not [long]::TryParse($champs[2], [ref]$debut) -or $port -lt 1 -or $port -gt 65535) {
        throw 'Registre HTTPS invalide.'
    }
    $processus = Get-Process -Id $pidServeur
    $debutProcessus = [DateTimeOffset]::new($processus.StartTime.ToUniversalTime()).ToUnixTimeMilliseconds()
    if ($debutProcessus -ne $debut -or $processus.ProcessName -ne 'java' -or -not $processus.Path) {
        throw 'Le processus du registre HTTPS est arrete ou ne correspond pas a Java.'
    }
    if (-not (Get-NetTCPConnection -State Listen -LocalPort $port | Where-Object { $_.OwningProcess -eq $pidServeur })) {
        throw 'Le processus HTTPS ne semble pas ecouter sur le port publie.'
    }
    Write-Host "Programme : $($processus.Path)"
    Write-Host "Port TCP  : $port"
    Write-Host 'Autoriser uniquement ce programme et ce port, depuis le sous-reseau local, sur le profil Prive.'
    Write-Host 'Sur un profil Public ou Domaine cette regle ne s applique pas.'
    if ((Read-Host 'Creer ou remplacer la regle pare-feu IHM HTTPS ? (O/N)') -notmatch '^[oO]$') {
        Write-Host 'Annule : aucune modification.'; exit 0
    }
    # Creer d'abord la nouvelle regle : une erreur ne supprime pas la regle precedente.
    $nom = 'IHM2026-HTTPS-' + [guid]::NewGuid().ToString()
    New-NetFirewallRule -Name $nom -DisplayName 'IHM 2026 HTTPS (port courant)' -Group 'IHM2026 HTTPS' `
        -Direction Inbound -Action Allow -Enabled True -Profile Private -Protocol TCP -LocalPort $port `
        -RemoteAddress LocalSubnet -Program $processus.Path | Out-Null
    Get-NetFirewallRule -Group 'IHM2026 HTTPS' | Where-Object { $_.Name -ne $nom } | Remove-NetFirewallRule
    Write-Host '[OK] Regle creee. Retester depuis le poste client.'
    Write-Host 'Le port peut changer : relancer cet outil apres chaque redemarrage HTTPS.'
} catch {
    Write-Host "[ERREUR] $($_.Exception.Message)"
    exit 1
}
