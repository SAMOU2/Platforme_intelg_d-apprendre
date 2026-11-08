# Fabrique les trois clients Windows, avec un runtime prive et la CA publique de PC1.
# Ne jamais fournir ici le magasin .p12 de PC1 ni un mot de passe serveur.
param(
    [string]$CertificatPublic,
    [string]$EmpreinteSHA256,
    [string]$WixBin,
    [switch]$SansTelechargementWiX,
    [switch]$ForcerWiXIntegre
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Executer([string]$Programme, [string[]]$Arguments) {
    Write-Host ("`n> " + $Programme + ' ' + ($Arguments -join ' '))
    & $Programme @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Echec de $Programme (code $LASTEXITCODE)." }
}

# WiX v3.14.1 officiel. SHA-256 aussi epingle par tauri-bundler et MongoDB.
$wixUrl = 'https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip'
$wixSha256 = '6ac824e1642d6f7277d0ed7ea09411a508f6116ba6fae0aa5f2c7daa2ff43d31'
$dossierTempWix = $null

function EstDossierWix([string]$Dossier) {
    return $Dossier -and (Test-Path -LiteralPath (Join-Path $Dossier 'candle.exe') -PathType Leaf) -and
        (Test-Path -LiteralPath (Join-Path $Dossier 'light.exe') -PathType Leaf)
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
    # WiX utilise uniquement pendant cette fabrication : aucun PATH global n'est modifie.
    if ($WixBin -and $ForcerWiXIntegre) { throw 'Choisir -WixBin ou -ForcerWiXIntegre, pas les deux.' }
    $wixChoisi = $null
    if ($WixBin) {
        $WixBin = $WixBin.Trim().Trim('"')
        if (-not (EstDossierWix $WixBin)) { throw "Dossier WiX invalide : $WixBin (candle.exe et light.exe requis)." }
        $wixChoisi = (Resolve-Path -LiteralPath $WixBin).Path
    } elseif (-not $ForcerWiXIntegre) {
        $dossiersWix = @()
        if ($env:WIX) { $dossiersWix += (Join-Path $env:WIX 'bin') }
        $programmes86 = ${env:ProgramFiles(x86)}
        if ($programmes86 -and (Test-Path -LiteralPath $programmes86)) {
            $dossiersWix += @(Get-ChildItem -LiteralPath $programmes86 -Directory -Filter 'WiX Toolset v3*' |
                Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName 'bin' })
        }
        $candle = Get-Command candle.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($candle) { $dossiersWix += (Split-Path -Parent $candle.Source) }
        foreach ($dossier in $dossiersWix) {
            if (EstDossierWix $dossier) { $wixChoisi = $dossier; break }
        }
    }
    if (-not $wixChoisi) {
        # L'archive seule est conservee dans le projet (ignoree par Git, hors target/mvn clean).
        # Chaque utilisation re-verifie une copie privee avant extraction ; jamais d'exe non verifie.
        $cache = Join-Path $PSScriptRoot '.wix'
        $archiveCache = Join-Path $cache 'wix314-binaries.zip'
        if (-not (Test-Path -LiteralPath $archiveCache -PathType Leaf) -and $SansTelechargementWiX) {
            throw 'WiX introuvable et telechargement interdit. Installer WiX 3 ou relancer sans -SansTelechargementWiX.'
        }
        $dossierTempWix = Join-Path ([IO.Path]::GetTempPath()) ('ihm-wix-' + [Guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $dossierTempWix | Out-Null
        $archive = Join-Path $dossierTempWix 'wix314-binaries.zip'
        if (Test-Path -LiteralPath $archiveCache -PathType Leaf) {
            Write-Host "WiX integre : verification du cache $archiveCache"
            Copy-Item -LiteralPath $archiveCache -Destination $archive
        } else {
            Write-Host "Telechargement de WiX 3.14.1 depuis $wixUrl (environ 40 Mo)..."
            try {
                [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
                Invoke-WebRequest -Uri $wixUrl -OutFile $archive -UseBasicParsing -TimeoutSec 300
            } catch {
                throw "Telechargement WiX impossible : $($_.Exception.Message). Verifier Internet ou fournir -WixBin."
            }
        }
        $hashWix = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($hashWix -ne $wixSha256) {
            throw "Archive WiX incorrecte (SHA-256 : $hashWix). Aucun programme ne sera execute. Supprimer uniquement $archiveCache si ce cache est corrompu."
        }
        if (-not (Test-Path -LiteralPath $archiveCache -PathType Leaf)) {
            New-Item -ItemType Directory -Force -Path $cache | Out-Null
            Copy-Item -LiteralPath $archive -Destination $archiveCache
        }
        $wixChoisi = Join-Path $dossierTempWix 'bin'
        Expand-Archive -LiteralPath $archive -DestinationPath $wixChoisi
        if (-not (EstDossierWix $wixChoisi)) { throw 'Archive WiX verifiee mais fichiers candle.exe ou light.exe absents.' }
    }
    $env:PATH = $wixChoisi + ';' + $env:PATH
    Write-Host "WiX utilise : $wixChoisi"
    if (-not $CertificatPublic) {
        $defaut = Join-Path $racine 'postes\secrets\autorite-ihm.crt'
        if (Test-Path -LiteralPath $defaut -PathType Leaf) {
            Write-Host "Certificat public local propose : $defaut"
            $CertificatPublic = Read-Host 'Appuyer sur Entree pour ce fichier, ou saisir un autre chemin .crt/.cer'
            if (-not $CertificatPublic) { $CertificatPublic = $defaut }
        } else {
            Write-Host 'Certificat public de PC1 introuvable dans postes\secrets\autorite-ihm.crt.'
            Write-Host 'Copier uniquement le .crt de PC1 sur ce PC, ou preparer PC1 avant de continuer.'
            $CertificatPublic = Read-Host 'Chemin du certificat PUBLIC .crt ou .cer de PC1'
        }
    }
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
    Write-Host 'Sur PC1, depuis le dossier du projet, ouvrir un autre PowerShell et executer :'
    Write-Host '  & "$env:JAVA_HOME\bin\keytool.exe" -printcert -file .\postes\secrets\autorite-ihm.crt'
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
            # Le dossier par defaut de jpackage evite un repertoire intermediaire dans le profil
            # sans regle de suppression (ICE64). Le journal detaille conserve les erreurs WiX.
            Executer $jpackage @('--verbose', '--type', 'msi', '--name', $r.Nom, '--app-version', $version,
                '--vendor', 'Plateforme apprentissage', '--input', $entree, '--dest', $sortie,
                '--main-jar', 'plateforme.jar', '--main-class', 'fr.ihm2026.Launcher',
                '--runtime-image', $runtime, '--arguments', "--role=$($r.Role) --poste=$($r.Poste) --port=7777",
                '--win-per-user-install',
                '--win-menu', '--win-shortcut', '--win-upgrade-uuid', $r.UUID)
        }
        $msis = @(Get-ChildItem -LiteralPath $sortie -Filter '*.msi')
        if ($msis.Count -ne 3) { throw "Trois MSI attendus, $($msis.Count) trouves." }
        $sommes = @($msis | Sort-Object Name | ForEach-Object {
            $hash = Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256
            $hash.Hash + '  ' + $_.Name
        })
        Set-Content -LiteralPath (Join-Path $sortie 'SHA256SUMS.txt') -Value $sommes -Encoding ASCII
        Write-Host "`n[OK] Installateurs crees dans : $sortie" -ForegroundColor Green
        $msis | ForEach-Object { Write-Host $_.Name }
        Write-Host 'SHA256SUMS.txt contient les empreintes des trois MSI.'
    } finally { Pop-Location }
} catch {
    Write-Host "`n[ERREUR] $($_.Exception.Message)" -ForegroundColor Red
    exit 1
} finally {
    if ($dossierTempWix -and (Test-Path -LiteralPath $dossierTempWix)) {
        Remove-Item -LiteralPath $dossierTempWix -Recurse -Force -ErrorAction SilentlyContinue
    }
}
