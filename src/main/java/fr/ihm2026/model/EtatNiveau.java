package fr.ihm2026.model;

/** État d'un niveau pour un apprenant (RG-03) : c'est la donnée la plus lue de l'interface. */
public enum EtatNiveau {
    VERROUILLE("Verrouillé", "etat-verrouille"),
    EN_COURS("En cours", "etat-en-cours"),
    VALIDE("Validé", "etat-valide");

    private final String label;
    private final String styleClass;

    EtatNiveau(String label, String styleClass) {
        this.label = label;
        this.styleClass = styleClass;
    }

    public String label() {
        return label;
    }

    public String styleClass() {
        return styleClass;
    }
}
