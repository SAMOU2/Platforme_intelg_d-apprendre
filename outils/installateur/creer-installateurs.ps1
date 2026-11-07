# Fabrique les trois clients Windows, avec un runtime prive et la CA publique de PC1.
# Ne jamais fournir ici le magasin .p12 de PC1 ni un mot de passe serveur.
param(
    [string]$CertificatPublic,
    [string]$EmpreinteSHA256
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Executer([string]$Programme, [string[]]$Arguments) {
    Write-Host ("`n> " + $Programme + ' ' + ($Arguments -join ' '))
    & $Programme @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Echec de $Programme (code $LASTEXITCODE)." }
}

try {
    if ($env:OS -ne 'Windows_NT') { throw 'La fabrication des installateurs Windows doit etre lancee sur Windows.' }
    $racine = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
    if (-not $env:JAVA_HOME) { throw 'Definir JAVA_HOME vers un JDK 25 complet pour Windows.' }
    $java = Join-Path $env:JAVA_HOME 'bin\java.exe'
    $jlink = Join-Path $env:JAVA_HOME 'bin\jlink.exe'
    $jpackage = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'
    $keytool = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
    foreach ($outil in @($java, $jlink, $jpackage, $keytool)) {
        if (-not (Test-Path -LiteralPath $outil -PathType Leaf)) { throw "Outil du JDK introuvable : $outil" }
    }
    $fichierRelease = Join-Path $env:JAVA_HOME 'release'
    if (-not (Test-Path -LiteralPath $fichierRelease -PathType Leaf)) { throw 'Fichier release du JDK introuvable.' }
    $release = Get-Content -Raw -LiteralPath $fichierRelease
    if ($release -notmatch '(?m)^JAVA_VERSION="25(?:[.\-+"\s])') {
        throw 'Un JDK Windows 25 est requis dans JAVA_HOME.'
    }
    if ($release -notmatch '(?m)^OS_ARCH="(?:amd64|x86_64)"') {
        throw 'La fabrication et les installateurs sont prevus pour Windows x64.'
    }
    # jpackage a besoin des outils WiX v3 sur le PATH pour produire un MSI.
    if (-not (Get-Command candle.exe -ErrorAction SilentlyContinue) -or
        -not (Get-Command light.exe -ErrorAction SilentlyContinue)) {
        throw 'WiX Toolset 3 est requis : ajouter son dossier bin au PATH (candle.exe et light.exe).'
    }
    if (-not $CertificatPublic) { $CertificatPublic = Read-Host 'Chemin du certificat PUBLIC .crt ou .cer de PC1' }
    $CertificatPublic = $CertificatPublic.Trim().Trim('"')
    if ([IO.Path]::GetExtension($CertificatPublic).ToLowerInvariant() -notin @('.crt', '.cer')) {
        throw 'Fournir uniquement un certificat public .crt ou .cer, jamais une cle privee ou un .p12.'
    }
    $CertificatPublic = (Resolve-Path -LiteralPath $CertificatPublic).Path
    $dossierTarget = (Join-Path $racine 'target') + [IO.Path]::DirectorySeparatorChar
    if ($CertificatPublic.StartsWith($dossierTarget, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Le certificat doit etre hors de target/, car Maven clean supprime ce dossier.'
    }
    $cert = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($CertificatPublic)
    try {
        if ($cert.HasPrivateKey) { throw 'Le certificat fourni contient une cle privee.' }
        $extension = $cert.Extensions | Where-Object { $_.Oid.Value -eq '2.5.29.19' } | Select-Object -First 1
        if (-not $extension) { throw 'Le certificat ne declare pas une autorite de certification.' }
        $contraintes = New-Object System.Security.Cryptography.X509Certificates.X509BasicConstraintsExtension($extension, $extension.Critical)
        if (-not $contraintes.CertificateAuthority) { throw 'Le certificat ne declare pas une autorite de certification.' }
        if ((Get-Date) -lt $cert.NotBefore -or (Get-Date) -gt $cert.NotAfter) { throw 'Le certificat est expire ou pas encore valide.' }
        # Figer les octets valides : ne pas relire un fichier remplace pendant la compilation.
        $certificatVerifie = $cert.RawData
        $sha = [Security.Cryptography.SHA256]::Create()
        try { $empreinte = ([BitConverter]::ToString($sha.ComputeHash($certificatVerifie))).Replace('-', '') }
        finally { $sha.Dispose() }
    } finally { $cert.Dispose() }
    Write-Host "Empreinte SHA-256 du certificat : $empreinte"
    Write-Host 'Comparer avec PC1 par un canal fiable, independamment du fichier recu.'
    if (-not $EmpreinteSHA256) { $EmpreinteSHA256 = Read-Host 'Saisir l empreinte SHA-256 verifiee sur PC1' }
    $attendue = ($EmpreinteSHA256 -replace '[:\s-]', '').ToUpperInvariant()
    if ($attendue -notmatch '^[0-9A-F]{64}$' -or $attendue -ne $empreinte) {
        throw 'Empreinte invalide ou differente : aucun certificat ne sera importe.'
    }

    [xml]$pom = Get-Content -Raw -LiteralPath (Join-Path $racine 'pom.xml')
    $version = [string]$pom.project.version
    if ($version -notmatch '^\d+\.\d+\.\d+$') { throw 'La version Maven doit etre numerique (ex. 0.14.4) pour le MSI.' }
    $travail = Join-Path $racine 'target\installateur-windows'
    $entree = Join-Path $travail 'entree'
    $runtime = Join-Path $travail 'runtime'
    $sortie = Join-Path $racine 'target\installateurs'
    $env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
    Push-Location $racine
    try {
        # clean efface target/ ; jamais les donnees ni la configuration privee de PC1.
        Executer (Join-Path $racine 'mvnw.cmd') @('-B', 'clean', 'package', 'dependency:copy-dependencies', '-DincludeScope=runtime', "-DoutputDirectory=$entree")
        New-Item -ItemType Directory -Force -Path $entree, $sortie | Out-Null
        $jar = Join-Path $racine "target\apprentissage-$version.jar"
        if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw "JAR compile introuvable : $jar" }
        Copy-Item -LiteralPath $jar -Destination (Join-Path $entree 'plateforme.jar')
        if (-not (Get-ChildItem -LiteralPath $entree -Filter 'javafx-graphics-*-win.jar')) {
            throw 'Dependances JavaFX Windows absentes. Relancer la compilation depuis Windows.'
        }
        # Image distincte du JDK de fabrication : le certificat est importe uniquement ici.
        Executer $jlink @('--add-modules', 'java.se,jdk.crypto.ec,jdk.localedata,jdk.unsupported,jdk.charsets', '--strip-debug', '--no-header-files', '--no-man-pages', '--output', $runtime)
        $magasin = Join-Path $runtime 'lib\security\cacerts'
        if (-not (Test-Path -LiteralPath $magasin -PathType Leaf)) { throw 'Magasin de confiance absent du runtime embarque.' }
        $copieVerifiee = Join-Path $travail 'autorite-verifiee.cer'
        [IO.File]::WriteAllBytes($copieVerifiee, $certificatVerifie)
        Executer $keytool @('-importcert', '-noprompt', '-keystore', $magasin, '-storepass', 'changeit', '-alias', ('plateforme-ca-' + $empreinte.ToLowerInvariant()), '-file', $copieVerifiee)
        # UUID distincts et stables pour que Windows distingue les trois produits lors des mises a jour.
        $roles = @(
            @{ Nom='Plateforme Apprenant'; Role='apprenant'; Poste='PC4'; UUID='60521bc3-8562-4cf1-9553-d29cb52c7a58' },
            @{ Nom='Plateforme Enseignant'; Role='enseignant'; Poste='PC3'; UUID='6a13222e-1a34-4eea-9568-a069113ee7bb' },
            @{ Nom='Plateforme Administrateur'; Role='administrateur'; Poste='PC2'; UUID='ef057e55-a85c-470e-bacb-6dcfcf19ed43' }
        )
        foreach ($r in $roles) {
            Executer $jpackage @('--type', 'msi', '--name', $r.Nom, '--app-version', $version,
                '--vendor', 'Plateforme apprentissage', '--input', $entree, '--dest', $sortie,
                '--main-jar', 'plateforme.jar', '--main-class', 'fr.ihm2026.Launcher',
                '--runtime-image', $runtime, '--arguments', "--role=$($r.Role) --poste=$($r.Poste) --port=7777",
                '--win-per-user-install', '--win-menu', '--win-shortcut', '--win-upgrade-uuid', $r.UUID)
        }
        Write-Host "`n[OK] Installateurs crees dans : $sortie" -ForegroundColor Green
        Get-ChildItem -LiteralPath $sortie -Filter '*.msi' | ForEach-Object { Write-Host $_.Name }
    } finally { Pop-Location }
} catch {
    Write-Host "`n[ERREUR] $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
