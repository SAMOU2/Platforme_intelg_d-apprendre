-- Réinitialisation par code à usage unique ; aucune clé ni mot de passe en clair.
BEGIN;
CREATE TABLE IF NOT EXISTS reinitialisations_mot_de_passe (
    utilisateur_id BIGINT PRIMARY KEY REFERENCES utilisateurs(id) ON DELETE CASCADE,
    empreinte_code TEXT NOT NULL,
    expire_le TIMESTAMPTZ NOT NULL,
    echecs INTEGER NOT NULL DEFAULT 0 CHECK (echecs BETWEEN 0 AND 5)
);
CREATE TABLE IF NOT EXISTS quotas_reinitialisation (
    cle TEXT PRIMARY KEY,
    debut TIMESTAMPTZ NOT NULL DEFAULT now(),
    nombre INTEGER NOT NULL DEFAULT 1
);
COMMIT;
