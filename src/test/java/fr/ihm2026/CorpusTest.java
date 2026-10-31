package fr.ihm2026;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contrat du corpus embarqué (étape 2 — corpus v1).
 *
 * <p>Le corpus n'est plus un jeu de démonstration déséquilibré : c'est une grille de 6 notions
 * × 3 langages. Ces tests vérifient ce que le rapport de couverture affirme, sur le fichier
 * réellement livré dans l'application et pas sur la source Python qui l'a produit.</p>
 */
class CorpusTest {

    private static final List<String[]> LIGNES = new ArrayList<>();

    @BeforeAll
    static void charger() throws Exception {
        try (InputStream in = CorpusTest.class.getResourceAsStream("/fr/ihm2026/ia/corpus-fragments.tsv")) {
            assertNotNull(in, "Le corpus embarqué est absent du classpath");
            BufferedReader lecteur = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String ligne;
            while ((ligne = lecteur.readLine()) != null) {
                if (ligne.isBlank()) {
                    continue;
                }
                String[] colonnes = ligne.split("\t", 3);
                assertEquals(3, colonnes.length, "Ligne malformée (langage, section, contenu) : " + ligne);
                LIGNES.add(colonnes);
            }
        }
    }

    @Test
    @DisplayName("Le corpus v1 contient 36 fragments, exactement 12 par langage")
    void corpusEquilibre() {
        assertEquals(36, LIGNES.size(), "18 cours × 2 blocs attendus");
        TreeMap<String, Integer> parLangage = new TreeMap<>();
        for (String[] c : LIGNES) {
            parLangage.merge(c[0], 1, Integer::sum);
        }
        assertEquals(Set.of("cpp", "java", "python"), parLangage.keySet(), "Trois langages exactement");
        for (String langage : parLangage.keySet()) {
            assertEquals(12, parLangage.get(langage), "Le langage " + langage + " doit porter 12 fragments");
        }
    }

    @Test
    @DisplayName("Aucun fragment n'est vide et chaque fragment porte une section")
    void fragmentsComplets() {
        for (String[] c : LIGNES) {
            assertFalse(c[0].isBlank(), "Langage manquant");
            assertFalse(c[1].isBlank(), "Section manquante pour un fragment " + c[0]);
            assertTrue(c[2].strip().length() >= 40, "Fragment trop court pour être cité : " + c[1]);
        }
    }

    @Test
    @DisplayName("Aucun fragment n'en double un autre (le corpus a été dédoublonné)")
    void aucunDoublon() {
        Set<String> vus = new HashSet<>();
        for (String[] c : LIGNES) {
            String plat = c[2].toLowerCase().replaceAll("[^a-z0-9]+", " ").strip();
            assertTrue(vus.add(plat), "Fragment en double dans la section " + c[1]);
        }
    }

    @Test
    @DisplayName("Chaque langage porte six cours distincts et deux fragments par cours")
    void sixCoursParLangage() {
        // Les titres de cours sont descriptifs (« Listes et dictionnaires en Python » pour la
        // notion Collections) : la grille 6 × 3 se vérifie donc par la structure — six sections
        // par langage, deux fragments par section — et non par le vocabulaire du titre.
        // Ce test a été écrit après le gel du jeu de questions : on ne renomme pas un cours
        // validé pour faire passer un test.
        for (String langage : List.of("python", "java", "cpp")) {
            List<String> sections = LIGNES.stream()
                    .filter(c -> c[0].equals(langage))
                    .map(c -> c[1])
                    .toList();
            long distinctes = sections.stream().distinct().count();
            assertEquals(6, distinctes, "Six cours attendus en " + langage + " : " + sections.stream().distinct().toList());
            for (String section : sections.stream().distinct().toList()) {
                assertEquals(2, sections.stream().filter(section::equals).count(),
                        "Deux blocs attendus pour " + section);
            }
        }
    }
}
