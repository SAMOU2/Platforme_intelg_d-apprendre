-- Sessions d'authentification : jetons opaques, limite d'inactivité et durée maximale.
BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'sessions'
          AND column_name = 'jeton'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'sessions'
          AND column_name = 'empreinte_jeton'
    ) THEN
        ALTER TABLE sessions RENAME COLUMN jeton TO empreinte_jeton;
    END IF;
END
$$;

UPDATE sessions
SET empreinte_jeton = encode(digest(empreinte_jeton, 'sha256'), 'hex')
WHERE empreinte_jeton !~ '^[0-9a-f]{64}$';

ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS derniere_activite TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS expiration_max TIMESTAMPTZ;

UPDATE sessions
SET derniere_activite = COALESCE(derniere_activite, cree_le),
    expiration_max = COALESCE(expiration_max, cree_le + INTERVAL '8 hours');

ALTER TABLE sessions
    ALTER COLUMN derniere_activite SET DEFAULT now(),
    ALTER COLUMN derniere_activite SET NOT NULL,
    ALTER COLUMN expiration_max SET NOT NULL;

CREATE TABLE IF NOT EXISTS tentatives_authentification (
    email_normalise       TEXT NOT NULL,
    empreinte_source      TEXT NOT NULL,
    debut_fenetre         TIMESTAMPTZ NOT NULL DEFAULT now(),
    echecs                INTEGER NOT NULL DEFAULT 0 CHECK (echecs >= 0),
    PRIMARY KEY (email_normalise, empreinte_source)
);

COMMIT;
