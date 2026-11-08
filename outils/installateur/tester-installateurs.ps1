# Recette automatisable Windows : MSI par utilisateur, contenu, arguments et CA.
# A executer sur une machine de test : installation et desinstallation reelles.
param([Parameter(Mandatory = $true)][string]$EmpreinteSHA256)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Msiexec([string[]]$Options) {
    $processus = Start-Process -FilePath 'msiexec.exe' -ArgumentList $Options -Wait -PassThru
    if ($processus.ExitCode -ne 0) { throw "msiexec a echoue (code $($processus.ExitCode))." }
}

try {
    if ($env:OS -ne 'Windows_NT') { throw 'Les tests MSI necessitent Windows.' }
    $empreinte = ($EmpreinteSHA256 -replace '[:\s-]', '').ToLowerInvariant()
    if ($empreinte -notmatch '^[0-9a-f]{64}$') { throw 'Empreinte SHA-256 invalide.' }
    $racine = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
    $sortie = Join-Path $racine 'target\installateurs'
    $roles = @(
        @{ Nom='Plateforme Apprenant'; Role='apprenant'; Poste='PC4' },
        @{ Nom='Plateforme Enseignant'; Role='enseignant'; Poste='PC3' },
        @{ Nom='Plateforme Administrateur'; Role='administrateur'; Poste='PC2' }
    )
    $keytool = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
    foreach ($r in $roles) {
        $candidats = @(Get-ChildItem -LiteralPath $sortie -File -Filter ($r.Nom + '-*.msi'))
        if ($candidats.Count -ne 1) { throw "Un seul MSI attendu pour $($r.Nom)." }
        $msi = $candidats[0].FullName
        $programmes = Join-Path $env:LOCALAPPDATA 'Programs'
        if (Test-Path -LiteralPath $programmes) {
            $existant = @(Get-ChildItem -LiteralPath $programmes -Filter ($r.Nom + '.exe') -File -Recurse)
            if ($existant.Count -gt 0) { throw "Poste deja installe : utiliser une machine de test vierge pour $($r.Nom)." }
        }
        $installe = $false
        try {
            Msiexec @('/i', ('"' + $msi + '"'), '/qn', '/norestart')
            $installe = $true
            $exe = @(Get-ChildItem -LiteralPath $programmes -Filter ($r.Nom + '.exe') -File -Recurse)
            if ($exe.Count -ne 1) { throw "Lanceur installe introuvable ou multiple pour $($r.Nom)." }
            $dossier = $exe[0].Directory.FullName
            $cfg = Join-Path $dossier ('app\' + $r.Nom + '.cfg')
            $jar = Join-Path $dossier 'app\plateforme.jar'
            $cacerts = Join-Path $dossier 'runtime\lib\security\cacerts'
            foreach ($fichier in @($cfg, $jar, $cacerts)) {
                if (-not (Test-Path -LiteralPath $fichier -PathType Leaf)) { throw "Fichier installe absent : $fichier" }
            }
            $config = Get-Content -Raw -LiteralPath $cfg
            foreach ($attendu in @("--role=$($r.Role)", "--poste=$($r.Poste)", '--port=7777')) {
                if (-not $config.Contains($attendu)) { throw "Argument absent de $cfg : $attendu" }
            }
            if (-not (Get-ChildItem -LiteralPath (Join-Path $dossier 'app') -Filter 'javafx-graphics-*-win.jar')) {
                throw "JavaFX Windows absent pour $($r.Nom)."
            }
            $jarTool = Join-Path $env:JAVA_HOME 'bin\jar.exe'
            $contenu = & $jarTool tf $jar
            if ($LASTEXITCODE -ne 0) { throw "Impossible de verifier le JAR de $($r.Nom)." }
            if ($contenu -match '(?i)(\.java$|\.p12$|\.pfx$|\.key$|configuration-PC1\.local\.cmd$)') {
                throw "Sources ou secrets potentiels dans le JAR de $($r.Nom)."
            }
            $export = Join-Path $env:TEMP ([Guid]::NewGuid().ToString() + '.cer')
            try {
                & $keytool -exportcert -keystore $cacerts -storepass changeit -alias ('plateforme-ca-' + $empreinte) -file $export
                if ($LASTEXITCODE -ne 0) { throw "CA verifiee absente du runtime de $($r.Nom)." }
                $cert = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($export)
                $sha = [Security.Cryptography.SHA256]::Create()
                try { $trouvee = ([BitConverter]::ToString($sha.ComputeHash($cert.RawData))).Replace('-', '').ToLowerInvariant() }
                finally { $sha.Dispose(); $cert.Dispose() }
                if ($trouvee -ne $empreinte) { throw "Mauvais certificat CA dans le runtime de $($r.Nom)." }
            } finally {
                if (Test-Path -LiteralPath $export) { Remove-Item -LiteralPath $export -Force }
            }
            $interdits = @(Get-ChildItem -LiteralPath $dossier -File -Recurse | Where-Object {
                $_.Extension -in @('.java', '.p12', '.pfx', '.key') -or $_.Name -eq 'configuration-PC1.local.cmd'
            })
            if ($interdits.Count -gt 0) { throw "Sources ou secrets potentiels dans l'installation de $($r.Nom)." }
            Write-Host "[OK] Installation et contenu : $($r.Nom)"
        } finally {
            if ($installe) { Msiexec @('/x', ('"' + $msi + '"'), '/qn', '/norestart') }
        }
        if (Test-Path -LiteralPath $exe[0].FullName) { throw "Desinstallation incomplete : $($r.Nom)." }
        Write-Host "[OK] Desinstallation : $($r.Nom)"
    }
} catch {
    Write-Host "[ERREUR] $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
