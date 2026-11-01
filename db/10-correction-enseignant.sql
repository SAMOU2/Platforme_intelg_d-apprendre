-- Parcours réel : affectation -> copie -> correction humaine -> publication.
-- Appliquer après 01 à 09, sans réinitialiser les données.
BEGIN;
CREATE TABLE IF NOT EXISTS affectations_pedagogiques (
 apprenant_id BIGINT PRIMARY KEY REFERENCES utilisateurs(id),
 enseignant_id BIGINT NOT NULL REFERENCES utilisateurs(id),
 affecte_par BIGINT NOT NULL REFERENCES utilisateurs(id),
 maj_le TIMESTAMPTZ NOT NULL DEFAULT now(),
 CHECK(apprenant_id <> enseignant_id)
);
ALTER TABLE questions ADD COLUMN IF NOT EXISTS active_pedagogique BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE tentatives ADD COLUMN IF NOT EXISTS enseignant_id BIGINT REFERENCES utilisateurs(id);
ALTER TABLE tentatives ADD COLUMN IF NOT EXISTS sujet_snapshot JSONB;
ALTER TABLE tentatives ADD COLUMN IF NOT EXISTS reponses_snapshot JSONB;
ALTER TABLE tentatives ADD COLUMN IF NOT EXISTS code_source TEXT;
ALTER TABLE tentatives ADD COLUMN IF NOT EXISTS note_brouillon NUMERIC(4,2) CHECK(note_brouillon BETWEEN 0 AND 20);
ALTER TABLE tentatives ADD COLUMN IF NOT EXISTS commentaire_brouillon TEXT;
ALTER TABLE tentatives ADD COLUMN IF NOT EXISTS decision_brouillon BOOLEAN;
ALTER TABLE tentatives ADD COLUMN IF NOT EXISTS publie_le TIMESTAMPTZ;
ALTER TABLE tentatives ADD COLUMN IF NOT EXISTS seuil_snapshot INT CHECK(seuil_snapshot BETWEEN 0 AND 20);
CREATE UNIQUE INDEX IF NOT EXISTS uq_copie_ouverte ON tentatives(apprenant_id,test_id)
 WHERE enseignant_id IS NOT NULL AND statut IN ('EN_COURS','SOUMISE');
CREATE INDEX IF NOT EXISTS ix_copies_enseignant ON tentatives(enseignant_id,statut);
COMMIT;
