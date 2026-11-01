-- Après migration 11. Les parcours historiques et copies sont conservés.
BEGIN;
CREATE TABLE IF NOT EXISTS parcours_enseignants (
 enseignant_id BIGINT NOT NULL REFERENCES utilisateurs(id) ON DELETE RESTRICT,
 langage_id BIGINT NOT NULL REFERENCES langages(id) ON DELETE RESTRICT,
 parcours_id BIGINT NOT NULL UNIQUE REFERENCES parcours(id) ON DELETE CASCADE,
 PRIMARY KEY (enseignant_id, langage_id)
);
COMMIT;
