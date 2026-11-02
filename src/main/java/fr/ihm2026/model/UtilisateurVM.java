package fr.ihm2026.model;

/** Une ligne de compte, côté administration, ou une ligne de résultats côté enseignant. */
public class UtilisateurVM {

    private final String nom;
    private final String email;
    private final String role;
    private final String statutInscription;
    private final String creeLe;
    private final String recommandationBot;
    private final String motifBot;
    private final boolean actif;
    private final int note;
    private final int tentatives;

    public UtilisateurVM(String nom, String email, String role, boolean actif, int note, int tentatives) {
        this(nom, email, role, actif, note, tentatives, actif ? "ACCEPTE" : "INACTIF");
    }

    public UtilisateurVM(String nom, String email, String role, boolean actif, int note, int tentatives,
            String statutInscription) {
        this(nom, email, role, actif, note, tentatives, statutInscription, "", "NON_EVALUEE", "");
    }

    public UtilisateurVM(String nom, String email, String role, boolean actif, int note, int tentatives,
            String statutInscription, String creeLe, String recommandationBot, String motifBot) {
        this.nom = nom;
        this.email = email;
        this.role = role;
        this.actif = actif;
        this.note = note;
        this.tentatives = tentatives;
        this.statutInscription = statutInscription;
        this.creeLe = creeLe == null ? "" : creeLe;
        this.recommandationBot = recommandationBot == null ? "NON_EVALUEE" : recommandationBot;
        this.motifBot = motifBot == null ? "" : motifBot;
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

    public String getCreeLe() {
        return creeLe;
    }

    public String getRecommandationBot() {
        return recommandationBot;
    }

    public String getMotifBot() {
        return motifBot;
    }

    public String getAvisBot() {
        return switch (recommandationBot) {
            case "ACCEPTATION_CONSEILLEE" -> "Bot : acceptation conseillée";
            case "REFUS_CONSEILLE" -> "Bot : refus conseillé";
            case "REVISION_ADMIN" -> "Bot : révision admin";
            case "DEJA_TRAITE" -> "Bot : déjà traité";
            default -> "Bot : non évalué";
        };
    }

    public boolean isActif() {
        return actif;
    }

    public String getEtatCompte() {
        return switch (statutInscription) {
            case "EN_ATTENTE" -> "En attente";
            case "REFUSE" -> "Refusé";
            case "ACCEPTE" -> actif ? "Actif" : "Accepté, inactif";
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
