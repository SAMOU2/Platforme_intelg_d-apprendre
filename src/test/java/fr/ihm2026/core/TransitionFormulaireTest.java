package fr.ihm2026.core;

import static org.junit.jupiter.api.Assertions.*;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Group;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TransitionFormulaireTest {
    @AfterEach void retablirAnimations() {
        Animations.activer();
        Animations.terminerTout();
    }

    @Test void transitionsEnSensOpposesEtEtatFinalVisible() {
        Group formulaire = new Group();
        Timeline aller = Animations.creerTransitionFormulaire(formulaire, true);
        Timeline retour = Animations.creerTransitionFormulaire(formulaire, false);
        assertEquals(18.0, valeur(aller.getKeyFrames().get(0), formulaire.translateXProperty()));
        assertEquals(-18.0, valeur(retour.getKeyFrames().get(0), formulaire.translateXProperty()));
        for (Timeline transition : new Timeline[] { aller, retour }) {
            KeyFrame fin = transition.getKeyFrames().get(1);
            assertEquals(Animations.AMPLE, fin.getTime());
            assertEquals(0.0, valeur(fin, formulaire.translateXProperty()));
            assertEquals(1.0, valeur(fin, formulaire.opacityProperty()));
        }
    }

    @Test void mouvementDesactiveAfficheImmediatementLeFormulaire() {
        Group formulaire = new Group();
        formulaire.setOpacity(0.3);
        formulaire.setTranslateX(18);
        Animations.desactiver();
        assertNull(Animations.transitionFormulaire(formulaire, true));
        assertEquals(1.0, formulaire.getOpacity());
        assertEquals(0.0, formulaire.getTranslateX());
        assertEquals(0, Animations.animationsEnCours());
        Animations.arreter(null);
    }

    private static double valeur(KeyFrame etape, javafx.beans.value.WritableValue<?> propriete) {
        return etape.getValues().stream().filter(v -> v.getTarget() == propriete)
                .map(KeyValue::getEndValue).map(Number.class::cast)
                .mapToDouble(Number::doubleValue).findFirst().orElseThrow();
    }
}
