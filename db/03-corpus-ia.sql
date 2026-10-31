-- =====================================================================
--  Étape 2 du développement de l'agent : corpus pédagogique destiné au RAG
--  et  étape 3 : table des fragments indexables (PostgreSQL + pgvector).
--
--  Le corpus n'est PAS saisi à part : il est extrait de la base de cours
--  (tables cours / blocs), ce qui garantit que l'agent cite toujours une
--  section réellement publiée. Les corrigés d'exercices notés sont exclus.
-- =====================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS vector;

-- ------------------------------------------------ compléments de corpus
-- Le jeu de démonstration ne contenait qu'un cours (Python, niveau 3).
-- Pour que l'agent soit utile dans les trois parcours, on ajoute l'équivalent
-- Java et C++ du même niveau, avec la même notion (fonctions et valeur de retour).
WITH niveau AS (
    SELECT n.id FROM niveaux n
    JOIN parcours p ON p.id = n.parcours_id
    JOIN langages l ON l.id = p.langage_id AND l.code = 'java'
    WHERE n.rang = 1
), c AS (
    INSERT INTO cours (niveau_id, titre, statut)
    SELECT id, 'Méthodes et valeur de retour', 'PUBLIE' FROM niveau
    WHERE NOT EXISTS (SELECT 1 FROM cours WHERE titre = 'Méthodes et valeur de retour')
    RETURNING id
)
INSERT INTO blocs (cours_id, position, type, langage, contenu)
SELECT c.id, v.position, v.type, v.langage, v.contenu
FROM c
JOIN (VALUES
    (1, 'TEXTE', NULL,
     'Une méthode regroupe des instructions et porte un type de retour. Si le type de retour est void, la méthode ne renvoie rien ; sinon, tous les chemins d''exécution doivent atteindre un return, faute de quoi le compilateur refuse la compilation.'),
    (2, 'CODE', 'java',
     'public static double moyenne(int[] notes) {
    if (notes.length == 0) {
        return 0;
    }
    int somme = 0;
    for (int n : notes) {
        somme += n;
    }
    return (double) somme / notes.length;
}'),
    (3, 'TEXTE', NULL,
     'En Java, un tableau connaît sa taille avec length (sans parenthèses), alors qu''une chaîne utilise length(). Confondre les deux est l''erreur de compilation la plus fréquente au niveau 3.')
) AS v(position, type, langage, contenu) ON TRUE;

WITH niveau AS (
    SELECT n.id FROM niveaux n
    JOIN parcours p ON p.id = n.parcours_id
    JOIN langages l ON l.id = p.langage_id AND l.code = 'cpp'
    WHERE n.rang = 1
), c AS (
    INSERT INTO cours (niveau_id, titre, statut)
    SELECT id, 'Fonctions, retour et surcharge', 'PUBLIE' FROM niveau
    WHERE NOT EXISTS (SELECT 1 FROM cours WHERE titre = 'Fonctions, retour et surcharge')
    RETURNING id
)
INSERT INTO blocs (cours_id, position, type, langage, contenu)
SELECT c.id, v.position, v.type, v.langage, v.contenu
FROM c
JOIN (VALUES
    (1, 'TEXTE', NULL,
     'En C++, le type de retour est déclaré avant le nom de la fonction. Une fonction déclarée sans type de retour valide ne compile pas ; le standard C++ impose que toute fonction non void retourne une valeur sur chaque chemin, sinon le comportement est indéfini.'),
    (2, 'CODE', 'cpp',
     '#include <vector>

double moyenne(const std::vector<int>& notes) {
    if (notes.empty()) {
        return 0.0;
    }
    int somme = 0;
    for (int n : notes) {
        somme += n;
    }
    return static_cast<double>(somme) / notes.size();
}'),
    (3, 'TEXTE', NULL,
     'La division entre deux entiers donne un entier : il faut convertir explicitement avec static_cast<double> avant de diviser, sinon 7 / 2 vaut 3 et non 3.5.')
) AS v(position, type, langage, contenu) ON TRUE;

-- Le cours Python de démonstration n'expliquait pas la valeur de retour : l'agent ne
-- pouvait donc pas répondre à « pourquoi ma fonction renvoie None ? ». On complète le
-- cours plutôt que d'inventer une réponse : le corpus est le seul levier de qualité.
INSERT INTO blocs (cours_id, position, type, langage, contenu)
SELECT c.id, 5, 'TEXTE', NULL,
       'Une fonction qui ne contient aucun return, ou dont le return est sans valeur, renvoie None : c''est la valeur de retour par défaut en Python. Pour renvoyer un résultat, écrivez return suivi de la valeur, et veillez à ce que tous les chemins d''exécution atteignent un return.'
FROM cours c
WHERE c.titre = 'Fonctions et modules'
  AND NOT EXISTS (SELECT 1 FROM blocs b WHERE b.cours_id = c.id AND b.position = 5);

-- ------------------------------------------------ fragments indexables
CREATE TABLE IF NOT EXISTS ia_fragments (
    id         BIGSERIAL PRIMARY KEY,
    niveau_id  BIGINT REFERENCES niveaux(id) ON DELETE CASCADE,
    cours_id   BIGINT REFERENCES cours(id)   ON DELETE CASCADE,
    langage    TEXT NOT NULL,
    section    TEXT NOT NULL,      -- titre affiché dans la citation
    ordre      INT  NOT NULL DEFAULT 0,
    contenu    TEXT NOT NULL,
    caracteres INT  NOT NULL,
    vecteur    vector(1536)        -- NULL tant que l'indexation n'a pas tourné
);

CREATE INDEX IF NOT EXISTS ix_fragments_vecteur
    ON ia_fragments USING hnsw (vecteur vector_cosine_ops);
CREATE INDEX IF NOT EXISTS ix_fragments_recherche
    ON ia_fragments USING gin (to_tsvector('french', contenu));
CREATE INDEX IF NOT EXISTS ix_fragments_langage ON ia_fragments (langage);

-- ------------------------------------------------ extraction du corpus
-- Un fragment = un bloc de cours. Le découpage fin (300-600 caractères) est
-- réalisé par ia/indexer.py, qui complète ensuite la colonne vecteur.
DELETE FROM ia_fragments;

INSERT INTO ia_fragments (niveau_id, cours_id, langage, section, ordre, contenu, caracteres)
SELECT n.id,
       c.id,
       COALESCE(b.langage, l.code),
       c.titre || ' — ' || n.titre,
       b.position,
       b.contenu,
       length(b.contenu)
FROM blocs b
JOIN cours c   ON c.id = b.cours_id
JOIN niveaux n ON n.id = c.niveau_id
JOIN parcours p ON p.id = n.parcours_id
JOIN langages l ON l.id = p.langage_id
WHERE c.statut = 'PUBLIE';

COMMIT;
