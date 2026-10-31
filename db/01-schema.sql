-- =====================================================================
--  Plateforme d'apprentissage Java / Python / C++ — schéma PostgreSQL 16
--  Projet d'IHM 2026 · v0.1
--  Convention : noms de tables au pluriel, identifiants BIGSERIAL,
--  horodatages en TIMESTAMPTZ, contraintes nommées pour les messages d'erreur.
-- =====================================================================

BEGIN;

-- ------------------------------------------------------------ extensions
CREATE EXTENSION IF NOT EXISTS "pgcrypto";   -- gen_random_uuid() pour les jetons

-- ------------------------------------------------------------- énumérations
CREATE TYPE role_utilisateur AS ENUM ('ADMINISTRATEUR', 'ENSEIGNANT', 'APPRENANT');
CREATE TYPE etat_niveau      AS ENUM ('VERROUILLE', 'EN_COURS', 'VALIDE');
CREATE TYPE statut_contenu   AS ENUM ('BROUILLON', 'PUBLIE', 'BROUILLON_IA');
CREATE TYPE statut_tentative AS ENUM ('EN_COURS', 'SOUMISE', 'CORRIGEE', 'ANNULEE');
CREATE TYPE type_question    AS ENUM ('QCM', 'REPONSE_COURTE', 'CODE');

-- --------------------------------------------------------------- comptes
CREATE TABLE utilisateurs (
    id              BIGSERIAL PRIMARY KEY,
    nom             TEXT NOT NULL,
    email           TEXT NOT NULL UNIQUE,
    mot_de_passe    TEXT NOT NULL,                       -- haché (BCrypt/Argon2) — jamais en clair
    role            role_utilisateur NOT NULL,
    actif           BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le         TIMESTAMPTZ NOT NULL DEFAULT now(),
    derniere_connexion TIMESTAMPTZ,
    CONSTRAINT ck_email CHECK (email LIKE '%@%.%')
);

-- Un seul rôle par compte : porté par la colonne role (pas de table de jointure),
-- ce qui rend l'invariant impossible à violer (modèle AD1, ALT « attribuer un rôle »).

-- ------------------------------------------------------------- parcours
CREATE TABLE langages (
    id              BIGSERIAL PRIMARY KEY,
    code            TEXT NOT NULL UNIQUE,                -- java | python | cpp
    libelle         TEXT NOT NULL,
    version         TEXT NOT NULL,                       -- version épinglée et affichée (RG-24)
    image_execution TEXT NOT NULL                        -- image du conteneur d'exécution (BNF-L5)
);

CREATE TABLE parcours (
    id              BIGSERIAL PRIMARY KEY,
    langage_id      BIGINT NOT NULL REFERENCES langages(id) ON DELETE RESTRICT,
    libelle         TEXT NOT NULL,
    actif           BOOLEAN NOT NULL DEFAULT TRUE
);

-- ------------------------------------------------------------- contenus
CREATE TABLE niveaux (
    id              BIGSERIAL PRIMARY KEY,
    parcours_id     BIGINT NOT NULL REFERENCES parcours(id) ON DELETE CASCADE,
    rang            INT NOT NULL CHECK (rang >= 1),
    titre           TEXT NOT NULL,
    objectifs       TEXT,
    seuil           INT NOT NULL CHECK (seuil BETWEEN 0 AND 20),      -- RG-04
    tentatives_max  INT NOT NULL DEFAULT 3 CHECK (tentatives_max >= 1),-- RG-08
    periode_debut   DATE,
    periode_fin     DATE,
    statut          statut_contenu NOT NULL DEFAULT 'BROUILLON',
    cree_par        BIGINT REFERENCES utilisateurs(id) ON DELETE SET NULL,
    cree_le         TIMESTAMPTZ NOT NULL DEFAULT now(),
    maj_le          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_niveau_rang UNIQUE (parcours_id, rang),
    CONSTRAINT ck_periode CHECK (periode_fin IS NULL OR periode_debut IS NULL
                                 OR periode_fin >= periode_debut)
);

CREATE TABLE cours (
    id              BIGSERIAL PRIMARY KEY,
    niveau_id       BIGINT NOT NULL REFERENCES niveaux(id) ON DELETE CASCADE,
    titre           TEXT NOT NULL,
    statut          statut_contenu NOT NULL DEFAULT 'BROUILLON',
    maj_le          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Un cours peut être commun aux trois langages (RG-23) : les blocs de code
-- portent le langage, le cours non.
CREATE TABLE blocs (
    id              BIGSERIAL PRIMARY KEY,
    cours_id        BIGINT NOT NULL REFERENCES cours(id) ON DELETE CASCADE,
    position        INT NOT NULL,
    type            TEXT NOT NULL CHECK (type IN ('TEXTE', 'CODE', 'IMAGE')),
    langage         TEXT,                                  -- renseigné seulement pour type = 'CODE'
    contenu         TEXT NOT NULL,
    CONSTRAINT uq_bloc_position UNIQUE (cours_id, position)
);

CREATE TABLE ressources (
    id              BIGSERIAL PRIMARY KEY,
    niveau_id       BIGINT NOT NULL REFERENCES niveaux(id) ON DELETE CASCADE,
    libelle         TEXT NOT NULL,
    url             TEXT NOT NULL
);

-- --------------------------------------------------------------- tests
CREATE TABLE tests (
    id              BIGSERIAL PRIMARY KEY,
    niveau_id       BIGINT NOT NULL UNIQUE REFERENCES niveaux(id) ON DELETE CASCADE,
    duree_minutes   INT NOT NULL DEFAULT 45 CHECK (duree_minutes > 0),
    ordre_aleatoire BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE questions (
    id              BIGSERIAL PRIMARY KEY,
    test_id         BIGINT NOT NULL REFERENCES tests(id) ON DELETE CASCADE,
    position        INT NOT NULL,
    type            type_question NOT NULL DEFAULT 'QCM',
    enonce          TEXT NOT NULL,
    bareme          NUMERIC(4,2) NOT NULL DEFAULT 1.00 CHECK (bareme > 0),
    CONSTRAINT uq_question_position UNIQUE (test_id, position)
);

CREATE TABLE propositions (
    id              BIGSERIAL PRIMARY KEY,
    question_id     BIGINT NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    position        INT NOT NULL,
    texte           TEXT NOT NULL,
    correcte        BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_proposition_position UNIQUE (question_id, position)
);

-- Une seule bonne réponse par question QCM (contrainte applicative + index partiel).
CREATE UNIQUE INDEX uq_une_bonne_reponse
    ON propositions (question_id) WHERE correcte;

-- ---------------------------------------------------------- tentatives
CREATE TABLE tentatives (
    id              BIGSERIAL PRIMARY KEY,
    apprenant_id    BIGINT NOT NULL REFERENCES utilisateurs(id) ON DELETE CASCADE,
    test_id         BIGINT NOT NULL REFERENCES tests(id) ON DELETE CASCADE,
    numero          INT NOT NULL,
    statut          statut_tentative NOT NULL DEFAULT 'EN_COURS',
    note            NUMERIC(4,2),
    commencee_le    TIMESTAMPTZ NOT NULL DEFAULT now(),
    soumise_le      TIMESTAMPTZ,
    CONSTRAINT uq_tentative_numero UNIQUE (apprenant_id, test_id, numero),
    CONSTRAINT ck_note CHECK (note IS NULL OR note BETWEEN 0 AND 20)
);

CREATE TABLE reponses (
    id              BIGSERIAL PRIMARY KEY,
    tentative_id    BIGINT NOT NULL REFERENCES tentatives(id) ON DELETE CASCADE,
    question_id     BIGINT NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    proposition_id  BIGINT REFERENCES propositions(id) ON DELETE SET NULL,
    texte_libre     TEXT,
    correcte        BOOLEAN,
    CONSTRAINT uq_reponse UNIQUE (tentative_id, question_id)
);

CREATE TABLE soumissions_code (
    id              BIGSERIAL PRIMARY KEY,
    tentative_id    BIGINT NOT NULL REFERENCES tentatives(id) ON DELETE CASCADE,
    langage         TEXT NOT NULL,
    source          TEXT NOT NULL,
    sortie          TEXT,
    erreur          TEXT,
    statut          TEXT NOT NULL DEFAULT 'EN_ATTENTE',   -- EN_ATTENTE | OK | ERREUR_COMPILATION | ERREUR_EXECUTION
    duree_ms        INT,
    cree_le         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------- progression
CREATE TABLE progressions (
    id              BIGSERIAL PRIMARY KEY,
    apprenant_id    BIGINT NOT NULL REFERENCES utilisateurs(id) ON DELETE CASCADE,
    niveau_id       BIGINT NOT NULL REFERENCES niveaux(id) ON DELETE CASCADE,
    etat            etat_niveau NOT NULL DEFAULT 'VERROUILLE',
    meilleure_note  NUMERIC(4,2),
    valide_le       TIMESTAMPTZ,
    maj_le          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_progression UNIQUE (apprenant_id, niveau_id)
);

-- Un niveau validé le reste : le seuil modifié plus tard ne réécrit pas le passé (RG-08 / E2).
CREATE OR REPLACE FUNCTION proteger_validation() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.etat = 'VALIDE' AND NEW.etat <> 'VALIDE' THEN
        RAISE EXCEPTION 'Une validation acquise ne peut pas être retirée (progressions.id=%)', OLD.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_proteger_validation
    BEFORE UPDATE ON progressions
    FOR EACH ROW EXECUTE FUNCTION proteger_validation();

-- --------------------------------------------------------------- audit
CREATE TABLE journal_audit (
    id              BIGSERIAL PRIMARY KEY,
    acteur_id       BIGINT REFERENCES utilisateurs(id) ON DELETE SET NULL,
    action          TEXT NOT NULL,                        -- CREATION_COMPTE, PUBLICATION_NIVEAU…
    cible           TEXT,
    details         JSONB,
    cree_le         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_date ON journal_audit (cree_le DESC);

-- ------------------------------------------------------- agent d'IA (IA1)
CREATE TABLE agent_ia (
    id              BIGSERIAL PRIMARY KEY,
    nom             TEXT NOT NULL UNIQUE,
    actif           BOOLEAN NOT NULL DEFAULT TRUE,
    positionnement  TEXT NOT NULL CHECK (positionnement IN ('A', 'B')),
    quota_jour      INT NOT NULL DEFAULT 200,
    cree_le         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE aides_ia (
    id              BIGSERIAL PRIMARY KEY,
    apprenant_id    BIGINT NOT NULL REFERENCES utilisateurs(id) ON DELETE CASCADE,
    tentative_id    BIGINT REFERENCES tentatives(id) ON DELETE SET NULL,
    agent_id        BIGINT REFERENCES agent_ia(id) ON DELETE SET NULL,
    question        TEXT NOT NULL,
    reponse         TEXT NOT NULL,
    modele          TEXT,
    cree_le         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ------------------------------------------------------------- sessions
CREATE TABLE sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    utilisateur_id  BIGINT NOT NULL REFERENCES utilisateurs(id) ON DELETE CASCADE,
    jeton           TEXT NOT NULL UNIQUE,
    expire_le       TIMESTAMPTZ NOT NULL,
    cree_le         TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMIT;
