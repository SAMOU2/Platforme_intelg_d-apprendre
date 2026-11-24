package fr.ihm2026.ui.ia;

import fr.ihm2026.api.ia.ContexteIa;
import fr.ihm2026.api.ia.EtatAgent;
import fr.ihm2026.api.ia.MotifRefus;
import fr.ihm2026.api.ia.ReponseIa;
import fr.ihm2026.api.service.AideIaApi;
import fr.ihm2026.core.Session;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Panneau d'assistance (IA1) — étape 8 du développement de l'agent.
 *
 * <p>Le panneau ne contient plus aucune réponse en dur : il appelle {@link AideIaApi}, qui applique
 * la chaîne quota → garde-fou d'entrée → modèle → garde-fou de sortie → journal. Les refus
 * ({@link MotifRefus}) sont affichés comme des orientations pédagogiques, jamais comme des erreurs.</p>
 *
 * <p>La règle « l'IA ne note pas » n'est donc pas portée par cet écran : même si le modèle triche,
 * c'est le filtre de sortie qui décide de ce qui s'affiche.</p>
 */
public class PanneauAssistant extends VBox {

    private boolean demandeEnCours;
    /** Vrai pendant une copie ouverte : le serveur refuse l'IA (RG-13), le panneau l'explique. */
    private boolean verrouille;
    /** Action proposee quand l'assistance est suspendue : rouvrir la copie en cours. */
    private Runnable reprise;
    private final java.util.List<ContexteIa.Tour> historique=new java.util.ArrayList<>();
    private final VBox messages = new VBox(8);
    private final TextField champ = new TextField();
    private final Label pied;
    private final Button reprendre = new Button("Reprendre la copie en cours");
    private final java.util.List<Button> actions = new java.util.ArrayList<>();

    private final java.util.function.Supplier<String> codeCourant;
    public PanneauAssistant() { this(() -> ""); }
    public PanneauAssistant(java.util.function.Supplier<String> codeCourant) {
        this.codeCourant = java.util.Objects.requireNonNull(codeCourant);
        getStyleClass().addAll("card", "panel-ia");
        setPrefWidth(320);
        setSpacing(8);

        Label titre = new Label("Assistant IA");
        titre.getStyleClass().add("titre-panneau");
        Label mention = new Label("Aide NVIDIA via PC1 · ne note pas, ne valide pas. Ne transmettez aucune donnée privée.");
        mention.getStyleClass().add("mention-ia");
        mention.setWrapText(true);

        messages.getStyleClass().add("messages-ia");
        messages.getChildren().add(bulle("Posez une question sur l'exercice, ou demandez un indice.",
                "ia-systeme"));
        ScrollPane scroll = new ScrollPane(messages);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Button indice = bouton("Donner un indice", "indice");
        Button erreur = bouton("Expliquer mon erreur", "erreur");
        Button cours = bouton("Renvoyer au cours", "cours");
        HBox actions = new HBox(6, indice, erreur, cours);
        HBox.setHgrow(indice, Priority.ALWAYS);

        champ.setAccessibleText("Question à l’assistant pédagogique");
        champ.setPromptText("Poser une question sur le cours…");
        champ.getStyleClass().add("champ");
        champ.setOnAction(e -> envoyerLibre());

        pied = new Label(textePied());
        pied.getStyleClass().add("mention-ia");
        pied.setWrapText(true);

        reprendre.getStyleClass().add("btn-secondaire");
        reprendre.setMaxWidth(Double.MAX_VALUE);
        reprendre.setVisible(false);
        reprendre.setManaged(false);
        reprendre.setOnAction(e -> {
            if (reprise != null) {
                reprise.run();
            }
        });

        getChildren().addAll(titre, mention, scroll, actions, champ, reprendre, pied);
        setPadding(new Insets(10));
        rafraichirEtat();
    }

    /**
     * Étape 4 — politique de repli. Quand le fournisseur est indisponible, l'aide est
     * <b>désactivée</b> : les commandes sont neutralisées et la raison est affichée en clair.
     * L'écran de cours, l'éditeur et le bouton de soumission, eux, ne changent pas.
     */
    private void rafraichirEtat() {
        EtatAgent etat = AideIaApi.etatAgent();
        // La copie peut demarrer apres l'affichage du panneau (sujet charge en arriere-plan) :
        // l'etat est relu ici, pas seulement au changement d'onglet.
        boolean suspendue = verrouille || Session.evaluationEnCours();
        boolean dispo = etat.disponible() && !demandeEnCours && !suspendue;
        champ.setDisable(!dispo);
        actions.forEach(b -> b.setDisable(!dispo));
        // Le chemin de sortie est propose la ou le blocage est explique, pas seulement decrit.
        boolean sortie = suspendue && reprise != null;
        reprendre.setVisible(sortie);
        reprendre.setManaged(sortie);
        if (suspendue) {
            // Explication explicite : un panneau visible mais inerte doit dire pourquoi.
            pied.setText("Assistance suspendue : une copie de test est ouverte. Reprenez "
                    + "l'onglet « 3 · Test final » pour la soumettre ou l'abandonner, puis "
                    + "revenez ici : l'aide IA se réactive.");
        } else if (demandeEnCours) {
            pied.setText("Consultation du cours en cours…");
        } else if (!etat.disponible()) {
            pied.setText(etat.explication());
        } else {
            pied.setText(textePied());
        }
    }

    /** Rappel des limites, affiche quand l'assistance est disponible. */
    private String textePied() {
        return "Modèle : " + AideIaApi.identifiantModele()
                + " · via PC1 : 30 demandes/jour par compte, quota partagé 200.";
    }

    /**
     * Suspend l'assistance (copie de test ouverte) ou la rend disponible, sans retirer le panneau
     * de l'écran : l'apprenant voit la règle au lieu de chercher un bouton qui ne repond plus.
     */
    public void verrouiller(boolean suspendue) {
        verrouille = suspendue;
        rafraichirEtat();
    }

    /** Bouton affiche pendant le verrouillage pour revenir a la copie ouverte. */
    public void surReprise(Runnable action) {
        reprise = action;
        rafraichirEtat();
    }

    private Button bouton(String libelle, String type) {
        Button b = new Button(libelle);
        b.getStyleClass().addAll("btn-secondaire", "btn-ia");
        b.setWrapText(true);
        b.setOnAction(e -> demander(type));
        HBox.setHgrow(b, Priority.ALWAYS);
        actions.add(b);
        return b;
    }

    private void demander(String type) {
        String question = switch (type) {
            case "indice" -> "Donne-moi un indice pour aborder cet entraînement dans le langage et le niveau sélectionnés, avec une première piste plutôt qu’un programme terminé.";
            case "erreur" -> "Aide-moi à comprendre mon code actuel. Si le message d’erreur manque, demande-le-moi avant de conclure.";
            default -> "Quelle notion du cours dois-je revoir pour comprendre cet entraînement ?";
        };
        echanger(question);
    }

    private void envoyerLibre() {
        String question = champ.getText();
        if (question == null || question.isBlank()) {
            return;
        }
        champ.clear();
        echanger(question.strip());
    }

    /** Un seul chemin : tout passe par le service, donc par les garde-fous. */
    private void echanger(String question) {
        if (demandeEnCours) return;
        demandeEnCours = true;
        rafraichirEtat();
        ajouter(bulle(question, "ia-utilisateur"));

        // Style rétro : un rouet de patience en monospace, comme avant les animations en pixels.
        javafx.animation.Timeline rouet = fr.ihm2026.core.StyleVisuel.estRetro()
                ? fr.ihm2026.ui.commun.Retro.demarrerRouet(pied, "consultation du cours…")
                : null;

        ContexteIa contexte = new ContexteIa(Session.rangNiveauCourant(), Session.langage(), question, codeCourant.get(),historique);
        fr.ihm2026.core.TaskRunner.run(() -> AideIaApi.demander(contexte), reponse -> {
            demandeEnCours = false;
            if(!reponse.estRefus() && question.length()<=2000) {
                historique.add(new ContexteIa.Tour(question,reponse.reponse()));
                while(historique.size()>ContexteIa.TOURS_MAX)historique.remove(0);
            }
            afficherReponse(reponse, rouet);
        }, erreur -> {
            demandeEnCours = false;
            // Meme regle qu'ailleurs : la cause reelle accompagne le refus, sans quoi l'apprenant
            // ne peut ni comprendre ni signaler la panne.
            String cause = erreur.getMessage() == null || erreur.getMessage().isBlank()
                    ? erreur.getClass().getSimpleName() : erreur.getMessage().strip();
            afficherReponse(ReponseIa.refusDetaille(MotifRefus.INDISPONIBLE,
                    fr.ihm2026.api.ia.ModeleSimule.VERSION_PROMPT, cause), rouet);
        });
    }

    private void afficherReponse(ReponseIa reponse, javafx.animation.Timeline rouet) {
        fr.ihm2026.ui.commun.Retro.arreterRouet(rouet);
        Label bulle = bulle(reponse.reponse(), reponse.estRefus() ? "ia-systeme" : "ia-reponse");
        ajouter(bulle);
        // Rétro : le texte s'écrit caractère par caractère ; macOS : il apparaît en fondu.
        if (fr.ihm2026.core.StyleVisuel.estRetro()) {
            fr.ihm2026.ui.commun.Retro.ecrire(bulle, reponse.reponse(), 18);
        }
        for (ReponseIa.Citation citation : reponse.citations()) {
            Label lien = new Label("→ " + citation.section() + " (" + citation.langage() + ")");
            lien.getStyleClass().add("mention-ia");
            lien.setWrapText(true);
            ajouter(lien);
        }
        // Le pied suit toujours l'etat reel : verrouillage, indisponibilite, ou rappel des limites.
        rafraichirEtat();
    }

    /** Ajoute une bulle au fil et la fait apparaître (fondu + montée, comme Messages). */
    private void ajouter(javafx.scene.Node node) {
        messages.getChildren().add(node);
        fr.ihm2026.core.Animations.apparaitreBulle(node);
    }

    private Label bulle(String texte, String style) {
        Label l = new Label(texte);
        l.setWrapText(true);
        l.getStyleClass().addAll("bulle-ia", style);
        l.setMaxWidth(280);
        return l;
    }
}
