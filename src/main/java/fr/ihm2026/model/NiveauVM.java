package fr.ihm2026.model;

/** Un niveau d'un parcours, tel que l'interface doit le montrer (IHM-01, IHM-02). */
public class NiveauVM {

    private final int rang;
    private final String titre;
    private final EtatNiveau etat;
    private final int seuil;
    private final int note;
    private final int tentativesRestantes;
    private final String langage;

    public NiveauVM(int rang, String titre, EtatNiveau etat, int seuil, int note,
                    int tentativesRestantes, String langage) {
        this.rang = rang;
        this.titre = titre;
        this.etat = etat;
        this.seuil = seuil;
        this.note = note;
        this.tentativesRestantes = tentativesRestantes;
        this.langage = langage;
    }

    public int getRang() {
        return rang;
    }

    public String getTitre() {
        return titre;
    }

    public EtatNiveau getEtat() {
        return etat;
    }

    public int getSeuil() {
        return seuil;
    }

    public int getNote() {
        return note;
    }

    public int getTentativesRestantes() {
        return tentativesRestantes;
    }

    public String getLangage() {
        return langage;
    }

    public String getLibelleEtat() {
        return etat.label();
    }

    /** Raison chiffrée du verrou ou du statut (IHM-02 : jamais de niveau grisé sans explication). */
    public String getExplication() {
        return switch (etat) {
            case VALIDE -> "Validé avec " + note + "/20 (seuil " + seuil + ")";
            case EN_COURS -> "En cours — " + tentativesRestantes + " tentative(s) restante(s)";
            case VERROUILLE -> "Débloqué après validation du niveau " + (rang - 1);
        };
    }
}
