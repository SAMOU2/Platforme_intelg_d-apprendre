-- Après migration 10 ; aucun contenu ni compte existant supprimé.
BEGIN;
CREATE TABLE IF NOT EXISTS entrainements_niveaux (
 id BIGSERIAL PRIMARY KEY,
 niveau_id BIGINT NOT NULL REFERENCES niveaux(id) ON DELETE CASCADE,
 enonce TEXT NOT NULL CHECK(length(enonce) BETWEEN 1 AND 12000),
 code_depart TEXT NOT NULL DEFAULT '',
 publie BOOLEAN NOT NULL DEFAULT FALSE,
 cree_par BIGINT REFERENCES utilisateurs(id),
 maj_le TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_entrainements_niveau ON entrainements_niveaux(niveau_id,publie);
COMMIT;
