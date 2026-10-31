-- =====================================================================
--  Étape 3 — recherche hybride sur le corpus (PostgreSQL + pgvector).
--
--  Pourquoi hybride : un vecteur seul rate les noms d'API (ArrayList,
--  std::vector) ; un index plein texte seul rate les reformulations. On
--  combine similarité cosinus et pertinence lexicale française, et on
--  renvoie séparément les deux composantes pour que l'appelant puisse
--  décider de CITER ou de REFUSER (étape 6).
-- =====================================================================

BEGIN;

DROP FUNCTION IF EXISTS rechercher_fragments(vector, text, text, int, double precision);

CREATE FUNCTION rechercher_fragments(
    question_vecteur vector(1536),
    question_texte   TEXT,
    filtre_langage   TEXT DEFAULT NULL,
    k                INT  DEFAULT 5,
    seuil_vecteur    DOUBLE PRECISION DEFAULT 0.05
)
RETURNS TABLE (
    fragment_id BIGINT,
    section     TEXT,
    langage     TEXT,
    contenu     TEXT,
    similitude  DOUBLE PRECISION,
    lexical     DOUBLE PRECISION,
    score       DOUBLE PRECISION
)
LANGUAGE sql STABLE AS $$
    WITH candidats AS (
        SELECT f.id,
               f.section,
               f.langage,
               f.contenu,
               1 - (f.vecteur <=> question_vecteur)                        AS sim,
               ts_rank_cd(to_tsvector('french', f.contenu),
                          plainto_tsquery('french', question_texte))::double precision AS lex
        FROM ia_fragments f
        WHERE f.vecteur IS NOT NULL
          AND (filtre_langage IS NULL OR f.langage = filtre_langage)
    )
    SELECT c.id, c.section, c.langage, c.contenu, c.sim, c.lex,
           c.sim + (0.5 * c.lex) AS score
    FROM candidats c
    -- Un fragment n'est candidat que s'il est proche OU lexicalement pertinent :
    -- sans ce filtre, la branche vectorielle renvoie toujours k lignes, même
    -- pour une question hors domaine (constaté et corrigé le 2026-10-27).
    WHERE c.sim >= seuil_vecteur OR c.lex > 0
    ORDER BY score DESC
    LIMIT k;
$$;

COMMIT;
