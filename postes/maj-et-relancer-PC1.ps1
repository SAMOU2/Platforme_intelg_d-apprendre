# Mise a jour du projet puis relance complete des fenetres, en un seul double-clic.
#
# Attention : ce script FERME les fenetres du projet en cours (processus Java du
# projet et fenetres intitulees "IHM 2026 - ..."). Une session ouverte est donc
# interrompue. Aucune donnee n'est supprimee : ni la base, ni les comptes, ni les
# certificats. Le script ne touche a aucun autre logiciel.
$ErrorActionPreference = 'Continue'
$projet = Split-Path -Parent $PSScriptRoot
Set-Location $projet
Write-Host '====================================================================='
Write-Host '  MISE A JOUR ET RLANCE DE LA PLATEFORME'
Write-Host '====================================================================='
Write-Host "Projet : $projet"
Write-Host ''

# 1) Fermer les fenetres du projet.
Write-Host '[1/4] Fermeture des fenetres du projet...'
Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -and $_.CommandLine -like "*$projet*" } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
Get-Process cmd -ErrorAction SilentlyContinue |
    Where-Object { $_.MainWindowTitle -like 'IHM 2026*' } |
    ForEach-Object { Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue }
Start-Sleep -Seconds 2
Write-Host '      Fait.'

# 2) Mise a jour depuis GitHub (branche main, avance rapide uniquement).
Write-Host '[2/4] Mise a jour depuis GitHub...'
git fetch origin
if ($LASTEXITCODE -ne 0) {
    Write-Host '[ERREUR] git fetch a echoue. Verifier la connexion et reessayer.' -ForegroundColor Red
    exit 1
}
$branche = (git rev-parse --abbrev-ref HEAD).Trim()
if ($branche -ne 'main') {
    Write-Host "[ERREUR] La copie locale est sur la branche $branche, pas sur main." -ForegroundColor Red
    Write-Host '         Ouvrir un terminal dans le projet et lancer :'
    Write-Host '           git checkout main'
    Write-Host '         puis relancer ce script.'
    exit 1
}
git merge --ff-only origin/main
if ($LASTEXITCODE -ne 0) {
    Write-Host '[ERREUR] La mise a jour automatique a echoue (modifications locales ?).' -ForegroundColor Red
    Write-Host '         Ne rien supprimer : ouvrir un terminal et lancer  git status  pour voir.'
    exit 1
}
Write-Host '      Projet a jour.'

# 3) Compilation.
Write-Host '[3/4] Compilation...'
& (Join-Path $projet 'mvnw.cmd') -B -DskipTests compile
if ($LASTEXITCODE -ne 0) {
    Write-Host '[ERREUR] La compilation a echoue. Lire les lignes ci-dessus.' -ForegroundColor Red
    exit 1
}
Write-Host '      Compilation OK.'

# 4) Relance : authentification, puis serveur de salle, puis PC4.
Write-Host '[4/4] Lancement des fenetres...'
Start-Process cmd.exe -WorkingDirectory $projet -ArgumentList '/k postes\authentification-PC1.bat'
$fichierPort = Join-Path $projet '.ihm-runtime\https.port'
if (Test-Path $fichierPort) { Remove-Item $fichierPort -Force -ErrorAction SilentlyContinue }
$attente = 0
while (-not (Test-Path $fichierPort) -and $attente -lt 240) {
    Start-Sleep -Seconds 3
    $attente += 3
    Write-Host "      Attente du port HTTPS annonce... $attente s"
}
if (-not (Test-Path $fichierPort)) {
    Write-Host '[ERREUR] Aucun port HTTPS annonce apres 4 minutes.' -ForegroundColor Red
    Write-Host '         Lire la fenetre d authentification : le message explique la cause.'
    exit 1
}
$port = ((Get-Content $fichierPort) -split ' ')[0]
Write-Host "      Port HTTPS annonce : $port"
Start-Process cmd.exe -WorkingDirectory $projet -ArgumentList '/k postes\1-serveur-PC1.bat'
Start-Sleep -Seconds 5
Start-Process cmd.exe -WorkingDirectory $projet -ArgumentList '/k postes\4-apprenant-PC4.bat'
Write-Host ''
Write-Host '[OK] Authentification, serveur de salle et PC4 sont lances.' -ForegroundColor Green
Write-Host "     Le port HTTPS de cette session est $port : il est repris automatiquement."
exit 0
