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

    private final VBox messages = new VBox(8);
    private final TextField champ = new TextField();
    private final Label pied;
    private final java.util.List<Button> actions = new java.util.ArrayList<>();

    public PanneauAssistant() {
        getStyleClass().addAll("card", "panel-ia");
        setPrefWidth(320);
        setSpacing(8);

        Label titre = new Label("Assistant IA");
        titre.getStyleClass().add("titre-panneau");
        Label mention = new Label("Aide optionnelle · ne note pas, ne valide pas");
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

        champ.setPromptText("Poser une question sur le cours…");
        champ.getStyleClass().add("champ");
        champ.setOnAction(e -> envoyerLibre());

        pied = new Label("Modèle : " + AideIaApi.identifiantModele()
                + " · quota " + AideIaApi.quota() + " questions/jour");
        pied.getStyleClass().add("mention-ia");
        pied.setWrapText(true);

        getChildren().addAll(titre, mention, scroll, actions, champ, pied);
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
        boolean dispo = etat.disponible();
        champ.setDisable(!dispo);
        actions.forEach(b -> b.setDisable(!dispo));
        if (!dispo) {
            pied.setText(etat.explication());
        }
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
            case "indice" -> "Comment la fonction doit-elle retourner sa valeur ?";
            case "erreur" -> "Pourquoi ma fonction renvoie None ?";
            default -> "Où revoir la notion de valeur de retour ?";
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
        ajouter(bulle(question, "ia-utilisateur"));

        // Style rétro : un rouet de patience en monospace, comme avant les animations en pixels.
        String piedAvant = pied.getText();
        javafx.animation.Timeline rouet = fr.ihm2026.core.StyleVisuel.estRetro()
                ? fr.ihm2026.ui.commun.Retro.demarrerRouet(pied, "consultation du cours…")
                : null;

        ContexteIa contexte = new ContexteIa(Session.rangNiveauCourant(), Session.langage(), question, "");
        ReponseIa reponse = AideIaApi.demander(contexte);

        fr.ihm2026.ui.commun.Retro.arreterRouet(rouet);
        if (piedAvant != null) {
            pied.setText(piedAvant);
        }
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
        if (pied != null) {
            pied.setText("Modèle : " + AideIaApi.identifiantModele()
                    + " · " + AideIaApi.appelsDuJour() + "/" + AideIaApi.quota() + " aujourd'hui");
        }
        rafraichirEtat();
        if (!AideIaApi.etatAgent().disponible()) {
            pied.setText(AideIaApi.etatAgent().explication());
        }
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
