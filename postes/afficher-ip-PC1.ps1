# Affichage avant compilation. Plusieurs cartes peuvent fournir plusieurs IP candidates.
$ErrorActionPreference = 'Stop'
try {
    $cartes = @(Get-NetIPConfiguration | Where-Object {
        $_.NetAdapter.Status -eq 'Up' -and $_.IPv4Address
    } | Sort-Object @{ Expression = { if ($_.IPv4DefaultGateway) { 0 } else { 1 } } }, InterfaceIndex)
    $trouve = $false
    foreach ($carte in $cartes) {
        foreach ($adresse in $carte.IPv4Address) {
            $ip = $adresse.IPAddress
            if ($ip -and $ip -notmatch '^(127\.|169\.254\.|0\.)') {
                Write-Output ("  IPv4 : {0}  -  {1}" -f $ip, $carte.InterfaceAlias)
                $trouve = $true
            }
        }
    }
    if (-not $trouve) {
        Write-Output '  Aucune IPv4 utilisable. Connecter PC1 au reseau local puis relancer.'
        exit 1
    }
    Write-Output '  Si plusieurs IP : choisir la carte du meme reseau que les clients.'
    exit 0
} catch {
    Write-Output '  Impossible de lire la configuration reseau Windows.'
    exit 1
}
