package fr.ihm2026.model;

import java.util.List;

/** Résultat d'une tentative : note, seuil, points manquants (IHM-03, BF-R3). */
public class ResultatVM {

    private final int note;
    private final int seuil;
    private final List<String> pointsManquants;
    private final List<String> coursARevoir;
    private final int tentativesRestantes;

    public ResultatVM(int note, int seuil, List<String> pointsManquants,
                      List<String> coursARevoir, int tentativesRestantes) {
        this.note = note;
        this.seuil = seuil;
        this.pointsManquants = pointsManquants;
        this.coursARevoir = coursARevoir;
        this.tentativesRestantes = tentativesRestantes;
    }

    public int getNote() {
        return note;
    }

    public int getSeuil() {
        return seuil;
    }

    public boolean isReussi() {
        return note >= seuil;
    }

    public int getEcart() {
        return Math.max(0, seuil - note);
    }

    public List<String> getPointsManquants() {
        return pointsManquants;
    }

    public List<String> getCoursARevoir() {
        return coursARevoir;
    }

    public int getTentativesRestantes() {
        return tentativesRestantes;
    }
}
