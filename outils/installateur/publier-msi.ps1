# Cree un brouillon de release GitHub a partir des MSI deja fabriques localement.
# Ne publie jamais le code source, les executables .exe, les cles ou la configuration PC1.
param(
    [string]$Tag,
    [string]$DossierInstallateurs
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$depot = 'SAMOU2/Platforme_intelg_d-apprendre'
$documentTemporaire = $null

try {
    if ($env:OS -ne 'Windows_NT') { throw 'Cette publication doit etre lancee sur le PC Windows qui a fabrique les MSI.' }
    foreach ($outil in @('git', 'gh')) {
        if (-not (Get-Command $outil -CommandType Application -ErrorAction SilentlyContinue)) {
            throw "Outil introuvable : $outil. Installer Git et GitHub CLI, puis relancer."
        }
    }
    $racine = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
    if (-not $DossierInstallateurs) { $DossierInstallateurs = Join-Path $racine 'target\installateurs' }
    $DossierInstallateurs = (Resolve-Path -LiteralPath $DossierInstallateurs).Path
    [xml]$pom = Get-Content -Raw -LiteralPath (Join-Path $racine 'pom.xml')
    $version = [string]$pom.project.version
    if ($version -notmatch '^\d+\.\d+\.\d+$') { throw 'Version Maven invalide ; une version numerique est requise.' }

    $fichiers = @()
    foreach ($role in @('Administrateur', 'Enseignant', 'Apprenant')) {
        $fichier = Join-Path $DossierInstallateurs "Plateforme $role-$version.msi"
        if (-not (Test-Path -LiteralPath $fichier -PathType Leaf)) {
            throw "MSI attendu introuvable : $fichier. Fabriquer les trois installateurs pour la version $version."
        }
        # Un MSI est un fichier Compound File Binary ; refuser un simple .exe renomme.
        $flux = [IO.File]::OpenRead($fichier)
        try {
            $signature = New-Object byte[] 8
            if ($flux.Read($signature, 0, 8) -ne 8 -or
                ([BitConverter]::ToString($signature) -ne 'D0-CF-11-E0-A1-B1-1A-E1')) {
                throw "Le fichier n'a pas la signature d'un MSI : $fichier"
            }
        } finally { $flux.Dispose() }
        $fichiers += $fichier
    }
    Push-Location $racine
    try {
        $commit = (& git rev-parse --verify HEAD).Trim()
        if ($LASTEXITCODE -ne 0 -or $commit -notmatch '^[a-fA-F0-9]{40}$') { throw 'Commit Git du projet introuvable.' }
        Write-Host 'Verification de la connexion GitHub et du commit...'
        & gh auth status --hostname github.com
        if ($LASTEXITCODE -ne 0) { throw 'Connexion GitHub CLI indisponible. Configurer GitHub CLI sur ce PC avant de relancer.' }
        & gh api "repos/$depot/commits/$commit" --jq '.sha' | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Le commit $commit n'est pas accessible sur GitHub. Publier d'abord les modifications du projet." }
        if (-not $Tag) {
            $Tag = 'v' + $version + '-msi-' + (Get-Date).ToUniversalTime().ToString('yyyyMMdd-HHmmss')
        }
        if ($Tag -notmatch '^v[0-9][a-zA-Z0-9._-]{0,79}$') { throw 'Tag invalide : commencer par v suivi de lettres, chiffres, points, tirets ou underscores.' }
        # Ne jamais reutiliser un tag existant : --target serait alors ignore par GitHub CLI.
        & git ls-remote --exit-code --refs "https://github.com/$depot.git" "refs/tags/$Tag" | Out-Null
        $codeTag = $LASTEXITCODE
        if ($codeTag -eq 0) { throw "Le tag $Tag existe deja sur GitHub : choisir un autre tag." }
        if ($codeTag -ne 2) { throw 'Impossible de verifier les tags GitHub. Aucune release creee.' }
        $documentTemporaire = Join-Path ([IO.Path]::GetTempPath()) ('ihm-msi-' + [Guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $documentTemporaire | Out-Null
        $sommes = Join-Path $documentTemporaire 'SHA256SUMS.txt'
        $lignes = @($fichiers | ForEach-Object {
            $hash = Get-FileHash -LiteralPath $_ -Algorithm SHA256
            $hash.Hash + '  ' + [IO.Path]::GetFileName($_)
        })
        Set-Content -LiteralPath $sommes -Value $lignes -Encoding ASCII
        Write-Host "Creation du brouillon $Tag sur $depot, depuis $commit."
        Write-Host 'Seuls les trois MSI et leurs empreintes SHA-256 seront envoyes.'
        $assets = @($fichiers) + @($sommes)
        $notes = 'Installateurs Windows x64 pour administrateur, enseignant et apprenant. Ils contiennent la CA publique PC1 choisie a la fabrication. Verifier la provenance et tester avant publication. MSI non signes.'
        & gh release create $Tag @assets --repo $depot --target $commit --draft --title "Installateurs Windows $version" --notes $notes
        if ($LASTEXITCODE -ne 0) { throw 'Creation du brouillon echouee. Verifier la connexion, les droits et que le tag ne possede pas deja de release.' }
        Write-Host "[OK] Brouillon cree. Verifier les fichiers puis publier le brouillon depuis GitHub Releases."
    } finally { Pop-Location }
} catch {
    Write-Host "[ERREUR] $($_.Exception.Message)" -ForegroundColor Red
    exit 1
} finally {
    if ($documentTemporaire -and (Test-Path -LiteralPath $documentTemporaire)) {
        Remove-Item -LiteralPath $documentTemporaire -Recurse -Force -ErrorAction SilentlyContinue
    }
}
