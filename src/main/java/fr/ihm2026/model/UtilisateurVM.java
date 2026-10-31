package fr.ihm2026.model;

/** Une ligne de compte, côté administration, ou une ligne de résultats côté enseignant. */
public class UtilisateurVM {

    private final String nom;
    private final String email;
    private final String role;
    private final String statutInscription;
    private final boolean actif;
    private final int note;
    private final int tentatives;

    public UtilisateurVM(String nom, String email, String role, boolean actif, int note, int tentatives) {
        this(nom, email, role, actif, note, tentatives, actif ? "ACCEPTE" : "INACTIF");
    }

    public UtilisateurVM(String nom, String email, String role, boolean actif, int note, int tentatives,
            String statutInscription) {
        this.nom = nom;
        this.email = email;
        this.role = role;
        this.actif = actif;
        this.note = note;
        this.tentatives = tentatives;
        this.statutInscription = statutInscription;
    }

    public String getNom() {
        return nom;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public String getStatutInscription() {
        return statutInscription;
    }

    public boolean isActif() {
        return actif;
    }

    public String getEtatCompte() {
        return switch (statutInscription) {
            case "EN_ATTENTE" -> "En attente";
            case "REFUSE" -> "Refusé";
            default -> actif ? "Actif" : "Désactivé";
        };
    }

    public boolean isEnAttente() {
        return "EN_ATTENTE".equals(statutInscription);
    }

    public int getNote() {
        return note;
    }

    public int getTentatives() {
        return tentatives;
    }

    public boolean isEnDifficulte() {
        return note > 0 && note < 10;
    }
}
