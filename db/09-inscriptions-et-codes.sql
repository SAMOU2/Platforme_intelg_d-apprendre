-- Validation des inscriptions et codes de connexion à usage unique.
BEGIN;

ALTER TABLE utilisateurs
    ADD COLUMN IF NOT EXISTS statut_inscription TEXT NOT NULL DEFAULT 'ACCEPTE';

ALTER TABLE utilisateurs
    ALTER COLUMN statut_inscription SET DEFAULT 'EN_ATTENTE';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_utilisateurs_statut_inscription'
          AND conrelid = 'utilisateurs'::regclass
    ) THEN
        ALTER TABLE utilisateurs
            ADD CONSTRAINT ck_utilisateurs_statut_inscription
            CHECK (statut_inscription IN ('EN_ATTENTE', 'ACCEPTE', 'REFUSE'));
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS codes_connexion (
    identifiant UUID PRIMARY KEY,
    utilisateur_id BIGINT NOT NULL REFERENCES utilisateurs(id) ON DELETE CASCADE,
    empreinte_code TEXT NOT NULL,
    expire_le TIMESTAMPTZ NOT NULL,
    echecs INTEGER NOT NULL DEFAULT 0 CHECK (echecs >= 0),
    cree_le TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_codes_connexion_expiration
    ON codes_connexion (expire_le);

CREATE TABLE IF NOT EXISTS invitations_inscription (
    id UUID PRIMARY KEY,
    email_cible TEXT NOT NULL,
    role_cible TEXT NOT NULL
        CHECK (role_cible IN ('APPRENANT', 'ENSEIGNANT', 'ADMINISTRATEUR')),
    empreinte_pin TEXT NOT NULL,
    expire_le TIMESTAMPTZ NOT NULL,
    echecs INTEGER NOT NULL DEFAULT 0 CHECK (echecs BETWEEN 0 AND 5),
    utilise_le TIMESTAMPTZ,
    cree_par BIGINT REFERENCES utilisateurs(id) ON DELETE SET NULL,
    cree_le TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_invitations_inscription_actives
    ON invitations_inscription (lower(email_cible), role_cible)
    WHERE utilise_le IS NULL;

CREATE INDEX IF NOT EXISTS ix_invitations_inscription_expiration
    ON invitations_inscription (expire_le);

COMMIT;
