package fr.ihm2026.core;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.text.Font;

/**
 * Polices embarquées (v0.10.0).
 *
 * <p>La direction visuelle macOS repose sur la typographie : sans elle, une interface à pastilles
 * et à coins arrondis reste une interface Windows repeinte. Apple ne distribue pas SF Pro hors de
 * ses systèmes ; <b>Inter</b> est la police libre la plus proche (mêmes proportions, mêmes
 * hauteurs d'x) et elle est embarquée dans l'application, donc identique sur Windows, Linux et
 * macOS. {@code JetBrains Mono} joue le même rôle pour l'éditeur et les citations de code.</p>
 *
 * <p>Le chargement est silencieux en cas d'échec : la feuille de style prévoit les polices système
 * en repli ({@code SF Pro Text}, {@code Segoe UI}, {@code DejaVu Sans}). Une police absente ne doit
 * jamais empêcher l'application de démarrer.</p>
 */
public final class Fonts {

    private static final List<String> RESSOURCES = List.of(
            "/fr/ihm2026/fonts/Inter.ttf",
            "/fr/ihm2026/fonts/JetBrainsMono-Regular.ttf",
            "/fr/ihm2026/fonts/JetBrainsMono-Bold.ttf");

    private static final List<String> CHARGEES = new ArrayList<>();

    private Fonts() {
    }

    /** Charge les polices une seule fois ; retourne les familles effectivement disponibles. */
    public static synchronized List<String> charger() {
        if (!CHARGEES.isEmpty()) {
            return List.copyOf(CHARGEES);
        }
        for (String ressource : RESSOURCES) {
            try (var in = Fonts.class.getResourceAsStream(ressource)) {
                if (in == null) {
                    continue;
                }
                Font police = Font.loadFont(in, 13);
                if (police != null && !CHARGEES.contains(police.getFamily())) {
                    CHARGEES.add(police.getFamily());
                }
            } catch (Exception e) {
                System.err.println("[ui] police non chargée (" + ressource + ") : " + e);
            }
        }
        return List.copyOf(CHARGEES);
    }

    public static List<String> chargees() {
        return List.copyOf(CHARGEES);
    }

    /** true si la police d'interface est bien celle qui est embarquée. */
    public static boolean interfaceDisponible() {
        return charger().stream().anyMatch(f -> f.toLowerCase().contains("inter"));
    }
}
