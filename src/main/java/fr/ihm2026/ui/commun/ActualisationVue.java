package fr.ihm2026.ui.commun;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Window;
import javafx.util.Duration;

/** Polling limité à la vue affichée : une requête à la fois, réponses obsolètes ignorées. */
public final class ActualisationVue<T> {
    private final Node vue;
    private final Callable<T> lire;
    private final Consumer<T> afficher;
    private final Consumer<Throwable> erreur;
    private final Timeline minuterie;
    private final ChangeListener<Boolean> visibilite = (o,a,b) -> synchroniser();
    private final ChangeListener<Window> fenetreChangee = (o,a,b) -> observerFenetre(b);
    private Window fenetre;
    private boolean active, occupe;
    private long generation;
    private T precedent;

    public ActualisationVue(Node vue, Callable<T> lire, Consumer<T> afficher, Consumer<Throwable> erreur) {
        this.vue=vue;this.lire=lire;this.afficher=afficher;this.erreur=erreur;
        minuterie=new Timeline(new KeyFrame(Duration.seconds(5),e -> actualiser()));
        minuterie.setCycleCount(Timeline.INDEFINITE);
        vue.sceneProperty().addListener((o,avant,apres) -> {
            if(avant!=null)avant.windowProperty().removeListener(fenetreChangee);
            if(apres!=null)apres.windowProperty().addListener(fenetreChangee);
            observerFenetre(apres==null?null:apres.getWindow());
        });
        Scene scene=vue.getScene();
        if(scene!=null)scene.windowProperty().addListener(fenetreChangee);
        observerFenetre(scene==null?null:scene.getWindow());
    }
    private void observerFenetre(Window nouvelle) {
        if(fenetre!=null)fenetre.showingProperty().removeListener(visibilite);
        fenetre=nouvelle;
        if(fenetre!=null)fenetre.showingProperty().addListener(visibilite);
        synchroniser();
    }
    private void synchroniser() {
        boolean visible=vue.getScene()!=null && fenetre!=null && fenetre.isShowing();
        if(active==visible)return;
        active=visible;generation++;precedent=null;
        if(active){minuterie.playFromStart();actualiser();}else minuterie.stop();
    }
    public void actualiser() {
        if(!active || occupe)return;
        occupe=true;long requete=generation;
        Task<T> travail=new Task<>() { @Override protected T call() throws Exception {return lire.call();} };
        travail.setOnSucceeded(e -> {
            occupe=false;
            if(!active || requete!=generation)return;
            T resultat=travail.getValue();
            if(!Objects.equals(precedent,resultat)){precedent=resultat;afficher.accept(resultat);}
        });
        travail.setOnFailed(e -> {
            occupe=false;
            if(!active || requete!=generation)return;
            precedent=null;erreur.accept(travail.getException());
        });
        Thread thread=new Thread(travail,"actualisation-pedagogique");thread.setDaemon(true);thread.start();
    }
}
