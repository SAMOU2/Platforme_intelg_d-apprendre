# Uniquement certificat public fourni par le responsable PC1, empreinte verifiee hors reseau.
$ErrorActionPreference = 'Stop'
try {
    $fichier = Read-Host 'Chemin du certificat PUBLIC autorite-ihm.crt fourni par PC1 (jamais un .p12)'
    if ([IO.Path]::GetExtension($fichier) -notin @('.crt','.cer')) { throw 'Un certificat public .crt ou .cer est requis.' }
    $cert = New-Object Security.Cryptography.X509Certificates.X509Certificate2($fichier)
    $contrainte = $cert.Extensions | Where-Object { $_.Oid.Value -eq '2.5.29.19' }
    if (-not $contrainte -or -not $contrainte.CertificateAuthority) { throw 'Ce certificat ne declare pas une autorite de certification.' }
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $empreinte = ([BitConverter]::ToString($sha.ComputeHash($cert.RawData))).Replace('-','') }
    finally { $sha.Dispose() }
    Write-Host ('Certificat propose - SHA256 : ' + $empreinte)
    Write-Host 'Verifier cette empreinte directement sur PC1 (ou avec son responsable par un canal fiable). Ne pas faire confiance a une annonce Wi-Fi.'
    $attendue = (Read-Host 'Saisir l empreinte SHA256 obtenue independamment sur PC1').Replace(':','').Replace(' ','').ToUpperInvariant()
    if ($attendue -ne $empreinte) { throw 'Empreintes differentes. Aucun certificat importe.' }
    $keytool = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
    # Pas de mot de passe dans la commande : keytool le demande dans le terminal.
    & $keytool -importcert -cacerts -alias ('plateforme-ca-' + $empreinte.ToLowerInvariant()) -file $fichier
    if ($LASTEXITCODE -ne 0) { throw 'Import non effectue : verifier permissions administrateur, magasin Java ou alias deja present. Ne rien supprimer au hasard.' }
    Write-Host '[OK] Commande terminee. Verifier que keytool confirme l ajout puis relancer les clients utilisant ce JDK.'
} catch {
    Write-Host ('[ERREUR] ' + $_.Exception.Message) -ForegroundColor Red
    exit 1
}
