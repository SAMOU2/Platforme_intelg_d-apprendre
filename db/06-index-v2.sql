-- =====================================================================
--  Étape 3 — découpage et indexation (RAG), version 2 de l'index
--
--  Pourquoi une seconde table plutôt qu'une modification de `ia_fragments` :
--  pour pouvoir comparer. L'étape 2 a mesuré un corpus indexé « un bloc = un
--  fragment » (145 à 437 caractères, sans recouvrement). L'étape 3 découpe en
--  fragments de 300 à 600 caractères avec 15 % de recouvrement. Les deux index
--  doivent exister en même temps, sinon il n'y a pas de mesure avant/après sur
--  le même jeu de questions gelées — seulement une affirmation.
--
--  Ordre d'application : 01 → 02 → 03 → 05 → 04 → 06, puis
--    cd ia && python3 indexer.py --mode blocs        (remplit ia_fragments)
--             python3 indexer.py                     (remplit ia_fragments_v2)
-- =====================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS vector;

-- ------------------------------------------------ fragments découpés
CREATE TABLE IF NOT EXISTS ia_fragments_v2 (
    id         BIGSERIAL PRIMARY KEY,
    niveau_id  BIGINT REFERENCES niveaux(id) ON DELETE CASCADE,
    cours_id   BIGINT REFERENCES cours(id)   ON DELETE CASCADE,
    langage    TEXT NOT NULL,
    section    TEXT NOT NULL,        -- ce qui est affiché dans la citation
    notion     TEXT NOT NULL,        -- VAR, COND, BOUCLE, FONC, COLL, ERREUR
    ordre      INT  NOT NULL DEFAULT 0,
    type_bloc  TEXT NOT NULL,        -- TEXTE, CODE ou TEXTE+CODE
    contexte   TEXT NOT NULL,        -- « Cours … · Langage … · Type … » : sert à retrouver
    contenu    TEXT NOT NULL,        -- le texte cité, sans le contexte
    caracteres INT  NOT NULL,
    vecteur    vector(1536),
    CONSTRAINT ck_fragment_v2_taille CHECK (caracteres <= 600)
);

-- Le contexte est indexé avec le contenu : une question qui nomme le langage ou la
-- notion (« différence entre length et size() en Java ») trouve ainsi le bon fragment
-- même si ces mots n'apparaissent pas dans le cours lui-même.
CREATE INDEX IF NOT EXISTS ix_fragments_v2_vecteur
    ON ia_fragments_v2 USING hnsw (vecteur vector_cosine_ops);
CREATE INDEX IF NOT EXISTS ix_fragments_v2_recherche
    ON ia_fragments_v2 USING gin (to_tsvector('french', contexte || ' ' || contenu));
CREATE INDEX IF NOT EXISTS ix_fragments_v2_langage ON ia_fragments_v2 (langage);
CREATE INDEX IF NOT EXISTS ix_fragments_v2_notion  ON ia_fragments_v2 (notion);

-- ------------------------------------------------ recherche hybride v2
DROP FUNCTION IF EXISTS rechercher_fragments_v2(vector, text, text, int, double precision,
                                                double precision, double precision, text[]);

CREATE FUNCTION rechercher_fragments_v2(
    question_vecteur   vector(1536),
    question_texte     TEXT,
    filtre_langage     TEXT             DEFAULT NULL,
    k                  INT              DEFAULT 5,
    seuil_vecteur      DOUBLE PRECISION DEFAULT 0.05,
    poids_lex          DOUBLE PRECISION DEFAULT 0.50,
    bonus_couverture   DOUBLE PRECISION DEFAULT 0.15,
    jetons             TEXT[]           DEFAULT NULL
)
RETURNS TABLE (
    fragment_id BIGINT,
    section     TEXT,
    langage     TEXT,
    notion      TEXT,
    type_bloc   TEXT,
    contenu     TEXT,
    similitude  DOUBLE PRECISION,
    lexical     DOUBLE PRECISION,
    couverture  DOUBLE PRECISION,
    score       DOUBLE PRECISION
)
LANGUAGE sql STABLE AS $$
    WITH candidats AS (
        SELECT f.id,
               f.section,
               f.langage,
               f.notion,
               f.type_bloc,
               f.contenu,
               1 - (f.vecteur <=> question_vecteur) AS sim,
               ts_rank_cd(to_tsvector('french', f.contexte || ' ' || f.contenu),
                          plainto_tsquery('french', question_texte))::double precision AS lex,
               -- couverture : part des mots significatifs de la question réellement présents
               -- dans le fragment. C'est ce qui distingue « ArrayList » de « liste ».
               (SELECT count(*) FILTER (
                           WHERE to_tsvector('french', f.contexte || ' ' || f.contenu)
                                 @@ plainto_tsquery('french', jeton))::double precision
                       / greatest(cardinality(jetons), 1)
                  FROM unnest(coalesce(jetons, ARRAY[]::text[])) AS jeton) AS couv
          FROM ia_fragments_v2 f
         WHERE f.vecteur IS NOT NULL
           AND (filtre_langage IS NULL OR f.langage = filtre_langage)
    )
    SELECT c.id, c.section, c.langage, c.notion, c.type_bloc, c.contenu,
           c.sim, c.lex, c.couv,
           c.sim + poids_lex * c.lex + bonus_couverture * c.couv AS score
      FROM candidats c
     WHERE c.sim >= seuil_vecteur OR c.lex > 0 OR c.couv > 0
     ORDER BY score DESC
     LIMIT k;
$$;

COMMIT;
