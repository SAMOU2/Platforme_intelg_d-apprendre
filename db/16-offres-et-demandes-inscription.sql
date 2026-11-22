-- Offres de formation des enseignants et demandes d'inscription des apprenants.
-- Changement de règle : l'administrateur n'affecte plus un apprenant à un enseignant.
-- L'apprenant ouvre le catalogue des enseignants publiés dans sa propre interface,
-- clique sur « S'inscrire », saisit son nom ; la demande est transmise à l'enseignant
-- qui l'accepte (ou que le bot de l'offre accepte immédiatement). L'acceptation écrit
-- la ligne d'accès dans affectations_pedagogiques : les cours, exercices et tests
-- publiés par cet enseignant deviennent accessibles.
-- Appliquer après 01 à 15, sans réinitialiser les données.
BEGIN;

-- Ce que l'enseignant propose : une offre par langage, rattachée à son parcours.
CREATE TABLE IF NOT EXISTS offres_enseignants (
    enseignant_id    BIGINT NOT NULL REFERENCES utilisateurs(id) ON DELETE CASCADE,
    langage_id       BIGINT NOT NULL REFERENCES langages(id) ON DELETE RESTRICT,
    titre            TEXT NOT NULL,
    presentation     TEXT NOT NULL,
    auto_acceptation BOOLEAN NOT NULL DEFAULT FALSE,
    ouverte          BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le          TIMESTAMPTZ NOT NULL DEFAULT now(),
    maj_le           TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (enseignant_id, langage_id),
    CONSTRAINT ck_offre_titre CHECK (char_length(titre) BETWEEN 3 AND 180),
    CONSTRAINT ck_offre_presentation CHECK (char_length(presentation) BETWEEN 3 AND 2000)
);

CREATE INDEX IF NOT EXISTS ix_offres_enseignants_langage
    ON offres_enseignants (langage_id);

-- Une demande = un apprenant, un enseignant, le langage visé et le nom saisi.
CREATE TABLE IF NOT EXISTS demandes_inscription (
    id            BIGSERIAL PRIMARY KEY,
    apprenant_id  BIGINT NOT NULL REFERENCES utilisateurs(id) ON DELETE CASCADE,
    enseignant_id BIGINT NOT NULL REFERENCES utilisateurs(id) ON DELETE CASCADE,
    langage_id    BIGINT NOT NULL REFERENCES langages(id) ON DELETE RESTRICT,
    nom_demandeur TEXT NOT NULL,
    message       TEXT,
    statut        TEXT NOT NULL DEFAULT 'EN_ATTENTE',
    motif         TEXT,
    decide_par    TEXT,
    cree_le       TIMESTAMPTZ NOT NULL DEFAULT now(),
    decide_le     TIMESTAMPTZ,
    CONSTRAINT ck_demande_statut
        CHECK (statut IN ('EN_ATTENTE', 'ACCEPTEE', 'REFUSEE', 'ANNULEE')),
    CONSTRAINT ck_demande_decideur
        CHECK (decide_par IS NULL OR decide_par IN ('BOT', 'ENSEIGNANT', 'APPRENANT')),
    CONSTRAINT ck_demande_nom CHECK (char_length(nom_demandeur) BETWEEN 2 AND 120),
    CONSTRAINT ck_demande_message CHECK (message IS NULL OR char_length(message) <= 600),
    CONSTRAINT ck_demande_motif CHECK (motif IS NULL OR char_length(motif) <= 600),
    CONSTRAINT ck_demande_acteur CHECK (apprenant_id <> enseignant_id)
);

-- Une seule demande en attente par couple (apprenant, enseignant) ; l'historique des
-- demandes déjà décidées est conservé pour l'audit et le suivi des deux côtés.
CREATE UNIQUE INDEX IF NOT EXISTS ux_demandes_inscription_attente
    ON demandes_inscription (apprenant_id, enseignant_id)
    WHERE statut = 'EN_ATTENTE';

CREATE INDEX IF NOT EXISTS ix_demandes_inscription_enseignant
    ON demandes_inscription (enseignant_id, statut, cree_le DESC);

CREATE INDEX IF NOT EXISTS ix_demandes_inscription_apprenant
    ON demandes_inscription (apprenant_id, cree_le DESC);

COMMIT;
