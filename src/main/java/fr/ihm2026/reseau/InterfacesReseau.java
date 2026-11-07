package fr.ihm2026.reseau;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.List;
import java.util.Locale;

/**
 * Sélection des cartes réellement utilisables pour découvrir PC1 sur le LAN.
 *
 * <p>Sur un même PC Windows, Java voit aussi les cartes créées par WSL/Hyper-V
 * et les tunnels (Cloudflare WARP, VPN). Leurs adresses sont locales au poste,
 * mais ce ne sont pas les adresses du réseau de la salle : les retenir comme
 * adresse du serveur mène à des échecs HTTPS alors que PC1 répond sur le
 * Wi-Fi/Ethernet. On ne garde donc que les interfaces physiques montées,
 * non point-à-point et dont le nom ne désigne ni un adaptateur virtuel d'hôte
 * ni un tunnel. Une carte « Microsoft Hyper-V Network Adapter » d'une machine
 * invitée reste acceptée : dans une VM, c'est la vraie carte LAN du poste.</p>
 */
public final class InterfacesReseau {

    private static final List<String> MARQUEURS_A_EXCLURE = List.of(
            "vethernet", "virtual ethernet", "wsl", "docker", "virtualbox", "vmware",
            "cloudflare", "warp", "wintun", "wireguard", "openvpn", "vpn",
            "tunnel", "teredo", "isatap", "pseudo");

    private InterfacesReseau() {
    }

    /** Vrai si la carte peut porter la découverte/du trafic du réseau local de la salle. */
    public static boolean estLanPhysique(NetworkInterface carte) throws SocketException {
        return carte.isUp() && !carte.isLoopback() && !carte.isVirtual()
                && !carte.isPointToPoint()
                && nomAccepte(carte.getName() + " " + carte.getDisplayName());
    }

    /** Le nom (technique + affiché) ne doit désigner ni carte virtuelle d'hôte ni tunnel. */
    static boolean nomAccepte(String nomBrut) {
        String nom = nomBrut == null ? "" : nomBrut.toLowerCase(Locale.ROOT);
        return MARQUEURS_A_EXCLURE.stream().noneMatch(nom::contains);
    }
}
