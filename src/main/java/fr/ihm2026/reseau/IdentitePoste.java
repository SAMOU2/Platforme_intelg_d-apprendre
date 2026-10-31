package fr.ihm2026.reseau;

import fr.ihm2026.core.Session;

/**
 * Identité du poste sur lequel l'application est lancée (v0.13.0).
 *
 * <p>La plateforme fonctionne en postes de rôle distincts :</p>
 * <ul>
 *   <li>chaque poste humain reçoit son rôle par son fichier de lancement ;</li>
 *   <li>les écrans du rôle restent fermés jusqu'à la connexion au serveur local.</li>
 * </ul>
 *
 * <p>Les trois propriétés lues ici sont posées par les fichiers {@code postes/*.cmd} :</p>
 * <pre>
 *   -Dihm.poste=PC1            nom affiché du poste
 *   -Dihm.role=administrateur  rôle imposé (administrateur | enseignant | apprenant | serveur)
 *   -Dihm.serveur=192.168.1.10 adresse du poste serveur proposée par défaut
 *   -Dihm.port=7777            port d'écoute du serveur
 * </pre>
 *
 * <p>Sans rôle configuré, le poste ne peut pas ouvrir les écrans métier et affiche un message
 * indiquant comment démarrer avec le script de poste correspondant.</p>
 */
public final class IdentitePoste {

    /** Port par défaut du serveur local ; surchargeable par {@code -Dihm.port=}. */
    public static final int PORT_DEFAUT = 7777;

    private IdentitePoste() {
    }

    /** Nom du poste, affiché dans la barre de titre et dans le journal du serveur. */
    public static String nom() {
        String valeur = System.getProperty("ihm.poste", "PC1").strip();
        return valeur.isEmpty() ? "PC1" : valeur;
    }

    /** Rôle brut demandé au lancement ({@code serveur} compris), ou {@code ""}. */
    public static String roleBrut() {
        return System.getProperty("ihm.role", "").strip().toLowerCase();
    }

    /** Vrai si ce poste est le poste serveur : il n'ouvre alors aucune fenêtre. */
    public static boolean estServeur() {
        return "serveur".equals(roleBrut());
    }

    /**
     * Rôle imposé au poste, ou {@code null} si aucun rôle n'a été configuré.
     * Les valeurs acceptées sont tolérantes : {@code admin}, {@code administrateur} et
     * {@code administrateur} avec sa casse d'origine désignent le même rôle.
     */
    public static Session.Role role() {
        return switch (roleBrut()) {
            case "admin", "administrateur", "administrator" -> Session.Role.ADMINISTRATEUR;
            case "enseignant", "professeur", "teacher" -> Session.Role.ENSEIGNANT;
            case "apprenant", "eleve", "etudiant", "student" -> Session.Role.APPRENANT;
            default -> null;
        };
    }

    /** Adresse du serveur proposée par défaut dans l'écran de connexion. */
    public static String serveurDefaut() {
        return System.getProperty("ihm.serveur", "127.0.0.1").strip();
    }

    /** Port d'écoute du serveur (celui que l'on tape derrière l'adresse IP s'il diffère). */
    public static int port() {
        try {
            int p = Integer.parseInt(System.getProperty("ihm.port", String.valueOf(PORT_DEFAUT)).strip());
            return p > 0 && p < 65536 ? p : PORT_DEFAUT;
        } catch (NumberFormatException e) {
            return PORT_DEFAUT;
        }
    }

    /** Libellé court du poste : « PC2 · Administrateur », ou « PC1 · poste non configuré ». */
    public static String libelle() {
        Session.Role role = role();
        return nom() + " · " + (role == null ? "poste non configuré" : role.label());
    }
}
