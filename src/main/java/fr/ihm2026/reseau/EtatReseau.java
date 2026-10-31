package fr.ihm2026.reseau;

import java.util.List;

/**
 * Les données qui circulent entre les postes, sous forme d'enregistrements immuables
 * sérialisés en JSON par Jackson (aucune dépendance ajoutée : Jackson est déjà là pour l'agent).
 *
 * <p>Le contrat est volontairement minimal — c'est un réseau de salle de classe, pas une
 * architecture distribuée :</p>
 * <ul>
 *   <li>{@link Sante} : « le serveur est-il là ? », et quelle version parle-t-il ;</li>
 *   <li>{@link Poste} : qui est connecté, avec quel rôle, depuis quelle adresse ;</li>
 *   <li>{@link Resultat} : la note d'un test, publiée par le poste apprenant et lue par le poste
 *       enseignant ;</li>
 *   <li>{@link Reponse} : l'accusé de réception, toujours lisible par un humain.</li>
 * </ul>
 *
 * <p>Aucun de ces objets ne contient de décision pédagogique : le serveur transporte et horodate,
 * il ne note pas et ne valide rien de plus que ce que le poste apprenant a calculé.</p>
 */
public final class EtatReseau {

    private EtatReseau() {
    }

    /** Réponse de la sonde {@code GET /api/sante}. */
    public record Sante(String produit, String version, String role, int postes, int resultats,
                        String heure) {
    }

    /** Un poste connecté. {@code depuis} est une heure locale lisible (HH:mm:ss). */
    public record Poste(String poste, String role, String adresse, String depuis) {
    }

    /**
     * Une note publiée. {@code valide} est calculé par le poste apprenant à partir du seuil du
     * niveau ; le serveur ne fait que le recopier, il ne le décide pas.
     */
    public record Resultat(String apprenant, String langage, int niveau, int note, int seuil,
                           boolean valide, String heure) {
    }

    /** Accusé de réception : {@code ok} pour l'interface, {@code message} pour l'utilisateur. */
    public record Reponse(boolean ok, String message) {
    }

    /** Enveloppe de liste : Jackson sérialise mieux un objet qu'une liste nue à la racine. */
    public record Postes(List<Poste> postes) {
    }

    /** Idem pour les résultats. */
    public record Resultats(List<Resultat> resultats) {
    }
}
