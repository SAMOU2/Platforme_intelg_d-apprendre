# Diagnostic sans identifiants : teste uniquement l'ouverture TCP, pas TLS ni le compte.
$hoteServeur = (Read-Host 'Adresse IPv4 de PC1 (affichee sur PC1)').Trim()
$textePort = (Read-Host 'Port HTTPS actuel affiche dans la fenetre authentification PC1').Trim()
$ip = $null
$port = 0
if (-not [System.Net.IPAddress]::TryParse($hoteServeur, [ref]$ip) -or
    $ip.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork -or
    -not [int]::TryParse($textePort, [ref]$port) -or $port -lt 1 -or $port -gt 65535) {
    Write-Host '[ERREUR] Entrez une adresse IPv4 et un port entre 1 et 65535.'
    exit 1
}
Write-Host "Test TCP vers ${hoteServeur}:${port} (sans mot de passe)..."
$socket = New-Object System.Net.Sockets.TcpClient
try {
    $operation = $socket.BeginConnect($ip, $port, $null, $null)
    if (-not $operation.AsyncWaitHandle.WaitOne(5000)) { throw 'Delai depasse' }
    $socket.EndConnect($operation)
    Write-Host '[OK] TCP accessible. Cela ne prouve pas que TLS ou le compte fonctionnent.'
    Write-Host 'Si la connexion echoue encore, utilisez verifier-certificat-https.cmd et consultez PC1.'
} catch {
    Write-Host '[ECHEC] TCP inaccessible. Verifiez adresse, port actuel et service HTTPS sur PC1.'
    Write-Host 'Si le service est actif, verifier pare-feu, VPN et isolation des clients Wi-Fi.'
    Write-Host 'Sur PC1 : postes\autoriser-https-pare-feu.cmd (administrateur, reseau prive).'
    exit 1
} finally { $socket.Close() }
