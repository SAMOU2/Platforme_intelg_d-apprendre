# Preparation explicite PC1 : aucun import de confiance automatique sur les clients.
$ErrorActionPreference = 'Stop'
$original = $env:IHM_TLS_CA_PASSWORD
$secret = $null
try {
    Set-Location (Split-Path $PSScriptRoot -Parent)
    if (-not $env:IHM_TLS_CA_KEYSTORE) {
        $env:IHM_TLS_CA_KEYSTORE = Join-Path $PSScriptRoot 'secrets\autorite-reseau.p12'
    }
    if (Test-Path $env:IHM_TLS_CA_KEYSTORE) {
        throw 'Autorite deja presente : ne pas la remplacer. Utiliser son certificat public existant et sa configuration.'
    }
    if (-not $env:IHM_TLS_CA_PASSWORD) {
        $secret = Read-Host 'Nouveau mot de passe de la CA (12 caracteres minimum, a conserver en prive)' -AsSecureString
        $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
        try { $env:IHM_TLS_CA_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    }
    $java = Join-Path $env:JAVA_HOME 'bin\java.exe'
    & $java 'src/main/java/fr/ihm2026/api/service/IdentiteTlsReseau.java' initialiser
    if ($LASTEXITCODE -ne 0) { throw 'Preparation echouee : ne pas activer le mode automatique tant que la CA et son certificat public ne sont pas verifies.' }
    Write-Host '[OK] CA preparee. Voir docs/wifi-identite-https.md pour approuver son certificat public sur chaque JDK client et activer le mode.'
    Write-Host '[IMPORTANT] Le secret saisi n est pas enregistre automatiquement. Conservez-le dans votre configuration privee, sans le partager.'
} catch {
    Write-Host ('[ERREUR] ' + $_.Exception.Message) -ForegroundColor Red
    exit 1
} finally {
    $env:IHM_TLS_CA_PASSWORD = $original
    if ($secret) { $secret.Dispose() }
}
